package io.github.eternaldesires.fireclay.block;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.eternaldesires.fireclay.component.Masterwork;

/**
 * An amethyst bell that outputs redstone by distance to the nearest hostile (design §3.2). Mounted like a lever,
 * it gives a weak signal all around and a strong signal into the block it hangs from. Owned by the ceramics feature.
 */
public class SentryChimeBlock extends FaceAttachedHorizontalDirectionalBlock {
	public static final IntegerProperty POWER = BlockStateProperties.POWER;
	public static final BooleanProperty MASTERWORK = BooleanProperty.create("masterwork");

	public static final int SCAN_INTERVAL = 10;
	public static final int RANGE = 12;
	public static final int MASTERWORK_RANGE = 20;
	/** Detected hostiles glow for this many ticks (refreshed every scan while in range). */
	private static final int GLOW_TICKS = 60;
	private static final int RING_INTERVAL = 40;

	private static final VoxelShape FLOOR_SHAPE = Block.box(4, 0, 4, 12, 10, 12);
	private static final VoxelShape CEILING_SHAPE = Block.box(4, 6, 4, 12, 16, 12);
	private static final VoxelShape WALL_NORTH = Block.box(4, 4, 6, 12, 12, 16);
	private static final VoxelShape WALL_SOUTH = Block.box(4, 4, 0, 12, 12, 10);
	private static final VoxelShape WALL_EAST = Block.box(0, 4, 4, 10, 12, 12);
	private static final VoxelShape WALL_WEST = Block.box(6, 4, 4, 16, 12, 12);

	public SentryChimeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACE, AttachFace.FLOOR)
				.setValue(FACING, Direction.NORTH)
				.setValue(POWER, 0)
				.setValue(MASTERWORK, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING, POWER, MASTERWORK);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.setValue(MASTERWORK, Masterwork.isMasterwork(context.getItemInHand()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACE)) {
			case FLOOR -> FLOOR_SHAPE;
			case CEILING -> CEILING_SHAPE;
			case WALL -> switch (state.getValue(FACING)) {
				case SOUTH -> WALL_SOUTH;
				case EAST -> WALL_EAST;
				case WEST -> WALL_WEST;
				default -> WALL_NORTH;
			};
		};
	}

	public static int range(BlockState state) {
		return state.getValue(MASTERWORK) ? MASTERWORK_RANGE : RANGE;
	}

	/** {@code d ≤ 2 → 15}, else {@code clamp(ceil(15 × (1 − (d − 2) / (R − 2))), 1, 15)}. */
	public static int powerForDistance(double distance, int range) {
		if (distance <= 2.0) {
			return 15;
		}
		return Mth.clamp(Mth.ceil(15.0 * (1.0 - (distance - 2.0) / (range - 2.0))), 1, 15);
	}

	// --- Scanning ------------------------------------------------------------------------------------------------

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			level.scheduleTick(pos, this, SCAN_INTERVAL);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int range = range(state);
		Vec3 center = Vec3.atCenterOf(pos);
		double rangeSqr = (double) range * range;
		List<Mob> hostiles = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(range),
				mob -> mob instanceof Enemy && mob.isAlive() && mob.distanceToSqr(center) <= rangeSqr);

		double nearestSqr = Double.MAX_VALUE;
		for (Mob mob : hostiles) {
			nearestSqr = Math.min(nearestSqr, mob.distanceToSqr(center));
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS));
		}
		int power = hostiles.isEmpty() ? 0 : powerForDistance(Math.sqrt(nearestSqr), range);

		int oldPower = state.getValue(POWER);
		if (power != oldPower) {
			BlockState updated = state.setValue(POWER, power);
			level.setBlock(pos, updated, Block.UPDATE_ALL);
			updateNeighbours(updated, level, pos);
		}
		if (power > 0 && (oldPower == 0 || level.getGameTime() % RING_INTERVAL < SCAN_INTERVAL)) {
			ring(level, pos, power);
		}
		level.scheduleTick(pos, this, SCAN_INTERVAL);
	}

	private static void ring(ServerLevel level, BlockPos pos, int power) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f + power / 30.0f);
		level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.5);
	}

	// --- Redstone (lever-like) -----------------------------------------------------------------------------------

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWER);
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(POWER);
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getConnectedDirection(state) == direction ? state.getValue(POWER) : 0;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!movedByPiston && state.getValue(POWER) > 0) {
			updateNeighbours(state, level, pos);
		}
	}

	private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
		level.updateNeighborsAt(pos, this);
		level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
	}

	/** The blockstate carries the masterwork flag; the dropped item gets it back as an unsigned component. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return CeramicDrops.withMasterwork(super.getDrops(state, params), this, state.getValue(MASTERWORK));
	}
}
