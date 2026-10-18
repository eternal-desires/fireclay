package io.github.eternaldesires.fireclay.block;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.registry.FireclayAttachments;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;

/**
 * A blaze-glazed tile that burns like magma and slowly smelts items resting on it (design §3.2). Progress lives on
 * each item entity as the transient {@code fireclay:bake_progress} attachment. Owned by the ceramics feature.
 */
public class EmberTileBlock extends Block {
	public static final BooleanProperty MASTERWORK = BooleanProperty.create("masterwork");

	public static final int BAKE_INTERVAL = 20;
	public static final int BAKE_TIME = 200;
	public static final int MASTERWORK_BAKE_TIME = 100;
	/** Items smelted at once from one stack. */
	public static final int BATCH = 8;
	private static final double CRITERION_RADIUS = 8.0;

	private static final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> SMELTING = RecipeManager.createCheck(RecipeType.SMELTING);

	public EmberTileBlock(Properties properties) {
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

	public static int bakeTime(BlockState state) {
		return state.getValue(MASTERWORK) ? MASTERWORK_BAKE_TIME : BAKE_TIME;
	}

	/** Like magma: burns whoever walks on it without sneaking. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity
				&& !entity.isSteppingCarefully() && !entity.fireImmune()) {
			entity.hurtServer(serverLevel, entity.damageSources().hotFloor(), 1.0f);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			level.scheduleTick(pos, this, BAKE_INTERVAL);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		AABB above = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
		int bakeTime = bakeTime(state);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, above, ItemEntity::isAlive)) {
			int progress = item.getAttachedOrElse(FireclayAttachments.BAKE_PROGRESS, 0) + BAKE_INTERVAL;
			if (progress < bakeTime) {
				item.setAttached(FireclayAttachments.BAKE_PROGRESS, progress);
			} else {
				bake(level, pos, item);
			}
		}
		level.scheduleTick(pos, this, BAKE_INTERVAL);
	}

	/** Smelts up to {@link #BATCH} items of the stack; unsmeltable items are left alone. */
	private static void bake(ServerLevel level, BlockPos pos, ItemEntity item) {
		item.removeAttached(FireclayAttachments.BAKE_PROGRESS);
		ItemStack source = item.getItem();
		SingleRecipeInput input = new SingleRecipeInput(source);
		Optional<RecipeHolder<SmeltingRecipe>> recipe = SMELTING.getRecipeFor(input, level);
		if (recipe.isEmpty()) {
			return;
		}
		ItemStack result = recipe.get().value().assemble(input);
		if (result.isEmpty()) {
			return;
		}
		int count = Math.min(BATCH, source.getCount());
		result.setCount(result.getCount() * count);

		ItemStack remaining = source.copy();
		remaining.shrink(count);
		if (remaining.isEmpty()) {
			item.discard();
		} else {
			item.setItem(remaining);
		}
		ItemEntity baked = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), result.copy(), 0.0, 0.1, 0.0);
		level.addFreshEntity(baked);

		level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.2, item.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f + level.getRandom().nextFloat() * 0.3f);

		for (ServerPlayer player : PlayerLookup.around(level, Vec3.atCenterOf(pos), CRITERION_RADIUS)) {
			FireclayCriteria.POTTER_ACTION.trigger(player, PotterActionTrigger.EMBER_BAKED, result, false);
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return CeramicDrops.withMasterwork(super.getDrops(state, params), this, state.getValue(MASTERWORK));
	}
}
