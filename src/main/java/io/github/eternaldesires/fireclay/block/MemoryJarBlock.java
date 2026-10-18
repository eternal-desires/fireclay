package io.github.eternaldesires.fireclay.block;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.eternaldesires.fireclay.component.Masterwork;

/**
 * A lapis-glazed jar that stores experience (design §3.2). Empty hand deposits, sneak + empty hand withdraws.
 * Owned by the ceramics feature.
 */
public class MemoryJarBlock extends BaseEntityBlock {
	/** The memory vein faces the player who placed it. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** ceil(4 × xp / capacity). */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 4);
	public static final BooleanProperty MASTERWORK = BooleanProperty.create("masterwork");

	public MemoryJarBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(FILL, 0)
				.setValue(MASTERWORK, false));
	}

	/** Light emission for {@code BlockBehaviour.Properties#lightLevel}: fill × 2. */
	public static int lightLevel(BlockState state) {
		return state.getValue(FILL) * 2;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FILL, MASTERWORK);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(MASTERWORK, Masterwork.isMasterwork(context.getItemInHand()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return StorageJarBlock.SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MemoryJarBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof MemoryJarBlockEntity jar)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (jar.withdraw(player) > 0) {
				level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f,
						1.0f + level.getRandom().nextFloat() * 0.2f);
			}
		} else if (jar.isFull()) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.memory_jar.full"));
		} else if (jar.deposit(player) > 0) {
			level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f,
					0.6f + level.getRandom().nextFloat() * 0.2f);
			if (jar.isFull()) {
				player.sendOverlayMessage(Component.translatable("message.fireclay.memory_jar.full"));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof MemoryJarBlockEntity jar ? jar.getComparatorSignal() : 0;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return CeramicDrops.withBlockEntityComponents(super.getDrops(state, params), this, params);
	}

	/** Restores the stored XP (placement already applied the components; this refreshes {@code fill}). */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MemoryJarBlockEntity jar) {
			jar.applyComponentsFromItemStack(stack);
			jar.setXp(jar.getXp());
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof MemoryJarBlockEntity jar && jar.getXp() > 0) {
			CeramicDrops.dropForCreative(level, pos, this, jar);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
