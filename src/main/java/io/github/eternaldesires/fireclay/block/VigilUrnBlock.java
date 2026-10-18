package io.github.eternaldesires.fireclay.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/**
 * A glow-ink urn (light 15, so nothing hostile spawns near it) that pushes hostiles out of its radius and calms
 * creepers (design §3.2). Owned by the ceramics feature.
 */
public class VigilUrnBlock extends Block {
	public static final BooleanProperty MASTERWORK = BooleanProperty.create("masterwork");
	public static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

	public static final int PUSH_INTERVAL = 10;
	public static final int RADIUS = 8;
	public static final int MASTERWORK_RADIUS = 12;
	private static final double PUSH_HORIZONTAL = 0.6;
	private static final double PUSH_UP = 0.1;
	private static final int CHIME_COOLDOWN = 40;

	public VigilUrnBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(MASTERWORK, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MASTERWORK);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(MASTERWORK, Masterwork.isMasterwork(context.getItemInHand()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	public static int radius(BlockState state) {
		return state.getValue(MASTERWORK) ? MASTERWORK_RADIUS : RADIUS;
	}

	/** Hostile living entities, except the bosses and wardens in {@code #fireclay:vigil_immune}. */
	public static boolean isRepelled(Entity entity) {
		return entity instanceof LivingEntity && entity instanceof Enemy && entity.isAlive() && !entity.isSpectator()
				&& !entity.is(FireclayTags.EntityTypes.VIGIL_IMMUNE);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			level.scheduleTick(pos, this, PUSH_INTERVAL);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int radius = radius(state);
		Vec3 center = Vec3.atCenterOf(pos);
		double radiusSqr = (double) radius * radius;
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(radius),
				entity -> isRepelled(entity) && entity.distanceToSqr(center) <= radiusSqr);

		for (LivingEntity entity : targets) {
			double dx = entity.getX() - center.x;
			double dz = entity.getZ() - center.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 1.0E-4) {
				float angle = random.nextFloat() * Mth.TWO_PI;
				dx = Mth.cos(angle);
				dz = Mth.sin(angle);
				length = 1.0;
			}
			entity.push(dx / length * PUSH_HORIZONTAL, PUSH_UP, dz / length * PUSH_HORIZONTAL);
			entity.needsSync = true;
			if (entity instanceof Creeper creeper) {
				creeper.setSwellDir(-1);
			}
		}
		if (!targets.isEmpty() && level.getGameTime() % CHIME_COOLDOWN < PUSH_INTERVAL) {
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 0.9f + random.nextFloat() * 0.2f);
		}
		level.scheduleTick(pos, this, PUSH_INTERVAL);
	}

	/** An occasional ring of glow motes around the neck. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) != 0) {
			return;
		}
		for (int i = 0; i < 6; i++) {
			float angle = i * (Mth.TWO_PI / 6) + random.nextFloat() * 0.3f;
			double x = pos.getX() + 0.5 + Mth.cos(angle) * 0.45;
			double z = pos.getZ() + 0.5 + Mth.sin(angle) * 0.45;
			level.addParticle(ParticleTypes.GLOW, x, pos.getY() + 0.9, z, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return CeramicDrops.withMasterwork(super.getDrops(state, params), this, state.getValue(MASTERWORK));
	}
}
