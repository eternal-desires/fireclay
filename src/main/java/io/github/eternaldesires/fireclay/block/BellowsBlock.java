package io.github.eternaldesires.fireclay.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclaySounds;

/**
 * The bellows (contract §1, design §3.2). {@code facing} is the direction the nozzle points. It pumps when used
 * (not sneaking) or on a rising redstone edge, and feeds a kiln in front of the nozzle. Owned by the workshop feature.
 */
public class BellowsBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Compressed for {@value #PRESS_TICKS} ticks after a pump; pumps are refused meanwhile. */
	public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");

	public static final int PRESS_TICKS = 8;

	public BellowsBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(POWERED, false)
				.setValue(PRESSED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, PRESSED);
	}

	/** The nozzle points the way the player looks, so placing it while facing a kiln aims it at the kiln. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(FACING, context.getNearestLookingDirection())
				.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	/** A manual pump, with or without an item in hand. Sneaking leaves the bellows alone (so blocks can be placed). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (state.getValue(PRESSED)) {
			return InteractionResult.CONSUME;
		}
		if (level instanceof ServerLevel serverLevel) {
			level.setBlock(pos, pumpOnce(serverLevel, pos, state, player), Block.UPDATE_ALL);
		}
		return InteractionResult.SUCCESS;
	}

	/** Redstone: track {@code powered} and pump once on each rising edge. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		boolean powered = level.hasNeighborSignal(pos);
		if (powered == state.getValue(POWERED)) {
			return;
		}
		BlockState next = state.setValue(POWERED, powered);
		if (powered && !state.getValue(PRESSED)) {
			next = pumpOnce(serverLevel, pos, next, null);
		}
		level.setBlock(pos, next, Block.UPDATE_ALL);
	}

	/** The scheduled tick from a pump: the bellows springs back open. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PRESSED)) {
			level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
		}
	}

	/**
	 * Pumps the bellows at {@code pos} exactly as a use does ({@code player} non-null) or a redstone edge does
	 * ({@code player} null). Returns false, doing nothing, if there is no bellows there or it is still pressed.
	 * Server side; used by the block itself and by gametests.
	 */
	public static boolean pump(ServerLevel level, BlockPos pos, @Nullable Player player) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BellowsBlock bellows) || state.getValue(PRESSED)) {
			return false;
		}
		level.setBlock(pos, bellows.pumpOnce(level, pos, state, player), Block.UPDATE_ALL);
		return true;
	}

	/**
	 * One pump: puff, sound, and {@link KilnBlockEntity#pump} on a kiln in front of the nozzle. Returns the pressed
	 * state for the caller to set (so a redstone edge can update {@code powered} in the same block change).
	 */
	private BlockState pumpOnce(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
		Direction facing = state.getValue(FACING);
		level.scheduleTick(pos, this, PRESS_TICKS);

		Vec3 nozzle = Vec3.atCenterOf(pos).add(facing.getUnitVec3().scale(0.6));
		level.sendParticles(ParticleTypes.CLOUD, nozzle.x, nozzle.y, nozzle.z, 6, 0.08, 0.08, 0.08, 0.03);
		level.playSound(null, pos, FireclaySounds.BELLOWS_PUMP, SoundSource.BLOCKS, 0.8f,
				0.9f + level.getRandom().nextFloat() * 0.2f);

		if (level.getBlockEntity(pos.relative(facing)) instanceof KilnBlockEntity kiln
				&& kiln.pump(level, player)
				&& player instanceof ServerPlayer serverPlayer) {
			FireclayCriteria.POTTER_ACTION.trigger(serverPlayer, PotterActionTrigger.PUMPED);
		}
		return state.setValue(PRESSED, true);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
