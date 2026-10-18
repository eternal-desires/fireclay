package io.github.eternaldesires.fireclay.kiln;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclayParticles;

/**
 * The kiln block (contract §1, design §3.2): opens its menu, takes a kiln lining, reports the window-relative
 * comparator signal and drops its lining and contents when broken. The simulation lives in {@link KilnBlockEntity}.
 */
public class KilnBlock extends BaseEntityBlock {
	/** The front (mouth) faces this way. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Visual heat stage: 0 cold (&lt;100), 1 warm, 2 hot (400+), 3 white-hot (700+). */
	public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);
	public static final BooleanProperty LINED = BooleanProperty.create("lined");
	public static final BooleanProperty DAMPER = BooleanProperty.create("damper");

	private static final int[] LIGHT_BY_HEAT = {0, 7, 11, 15};

	public KilnBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(HEAT, 0)
				.setValue(LINED, false)
				.setValue(DAMPER, false));
	}

	/** Light emission for {@code BlockBehaviour.Properties#lightLevel}. */
	public static int lightLevel(BlockState state) {
		return LIGHT_BY_HEAT[state.getValue(HEAT)];
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HEAT, LINED, DAMPER);
	}

	/** The mouth faces the placing player. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KilnBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, FireclayBlockEntities.KILN, KilnBlockEntity::serverTick);
	}

	/** A kiln lining used on an unlined kiln installs it; anything else falls through to opening the menu. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(FireclayItems.KILN_LINING) || state.getValue(LINED)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.setBlock(pos, state.setValue(LINED, true), Block.UPDATE_ALL);
			serverLevel.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
			serverLevel.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					10, 0.4, 0.4, 0.4, 0.05);
			stack.consume(1, player);
			if (player instanceof ServerPlayer serverPlayer) {
				FireclayCriteria.POTTER_ACTION.trigger(serverPlayer, PotterActionTrigger.LINED);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Opens the kiln menu and makes the player its stoker. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof KilnBlockEntity kiln) {
			kiln.setStoker(player);
			player.openMenu(kiln);
		}
		return InteractionResult.SUCCESS;
	}

	/** The block entity spills the contents itself (preRemoveSideEffects); this only refreshes neighbours. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	/** A lined kiln also gives its lining back, whenever the kiln itself drops. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		if (!state.getValue(LINED) || drops.stream().noneMatch(drop -> drop.is(asItem()))) {
			return drops;
		}
		List<ItemStack> withLining = new ArrayList<>(drops);
		withLining.add(new ItemStack(FireclayItems.KILN_LINING));
		return withLining;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof KilnBlockEntity kiln ? kiln.getComparatorSignal() : 0;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Client ambience: vent smoke from heat 1, mouth flames and embers from heat 2, lava sparks at white heat. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int heat = state.getValue(HEAT);
		if (heat == 0) {
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY();
		double z = pos.getZ() + 0.5;

		if (random.nextInt(state.getValue(DAMPER) ? 2 : 4) == 0) {
			level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.25, y + 1.02,
					z + (random.nextDouble() - 0.5) * 0.25, 0.0, 0.05, 0.0);
		}
		if (heat < 2) {
			return;
		}

		Direction facing = state.getValue(FACING);
		double sideways = (random.nextDouble() - 0.5) * 0.4;
		double mouthX = x + facing.getStepX() * 0.52 + (facing.getAxis() == Direction.Axis.Z ? sideways : 0.0);
		double mouthZ = z + facing.getStepZ() * 0.52 + (facing.getAxis() == Direction.Axis.X ? sideways : 0.0);
		double mouthY = y + 0.2 + random.nextDouble() * 0.3;
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.FLAME, mouthX, mouthY, mouthZ, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(FireclayParticles.KILN_EMBER, x + (random.nextDouble() - 0.5) * 0.2, y + 1.02,
					z + (random.nextDouble() - 0.5) * 0.2, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(10) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.0f, false);
		}
		if (heat == 3 && random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.LAVA, mouthX, mouthY, mouthZ, 0.0, 0.0, 0.0);
		}
	}
}
