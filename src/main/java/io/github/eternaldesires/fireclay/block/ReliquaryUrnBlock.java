package io.github.eternaldesires.fireclay.block;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclaySounds;

/**
 * An echo-glazed urn that catches its owner's belongings on death (design §3.11). Empty-hand use binds an unbound
 * urn (or one bound to you), and retrieves the belongings when it is filled. Owned by the ceramics feature.
 */
public class ReliquaryUrnBlock extends BaseEntityBlock {
	/** True while the urn holds a dead owner's items. */
	public static final BooleanProperty FILLED = BooleanProperty.create("filled");
	public static final BooleanProperty MASTERWORK = BooleanProperty.create("masterwork");

	public ReliquaryUrnBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FILLED, false).setValue(MASTERWORK, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FILLED, MASTERWORK);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(MASTERWORK, Masterwork.isMasterwork(context.getItemInHand()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return VigilUrnBlock.SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReliquaryUrnBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, FireclayBlockEntities.RELIQUARY_URN, ReliquaryUrnBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof ReliquaryUrnBlockEntity urn)) {
			return InteractionResult.SUCCESS;
		}
		UUID id = player.getUUID();
		if (urn.getOwner() != null && !urn.getOwner().equals(id)) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.reliquary.other_owner"));
			return InteractionResult.SUCCESS;
		}

		ReliquaryData data = ReliquaryData.get(serverLevel.getServer());
		GlobalPos here = GlobalPos.of(serverLevel.dimension(), pos);
		Optional<ReliquaryData.Binding> binding = data.get(id);
		if (binding.isPresent() && binding.get().urn().equals(here) && binding.get().isFilled()) {
			retrieve(serverLevel, pos, player, urn, data, binding.get());
		} else if (binding.isPresent() && !binding.get().urn().equals(here) && binding.get().isFilled()) {
			// Rebinding would orphan the belongings waiting in the other urn.
			player.sendOverlayMessage(Component.translatable("message.fireclay.reliquary.occupied"));
		} else {
			data.bind(id, here);
			urn.setOwner(id, player.getScoreboardName());
			serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
			player.sendOverlayMessage(Component.translatable("message.fireclay.reliquary.bound"));
		}
		return InteractionResult.SUCCESS;
	}

	/** Returns every held stack (to its old slot when free), spends a charge, and crumbles the urn at zero. */
	private static void retrieve(ServerLevel level, BlockPos pos, Player player, ReliquaryUrnBlockEntity urn,
			ReliquaryData data, ReliquaryData.Binding binding) {
		Inventory inventory = player.getInventory();
		for (ItemStackWithSlot held : binding.held()) {
			ItemStack stack = held.stack().copy();
			int slot = held.slot();
			if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
			} else if (!inventory.add(stack) || !stack.isEmpty()) {
				Block.popResource(level, pos.above(), stack);
			}
		}
		inventory.setChanged();
		data.setHeld(player.getUUID(), List.of());

		int charges = urn.getCharges() - 1;
		if (charges > 0) {
			urn.setCharges(charges);
			urn.syncWithData(level);
			level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
			return;
		}
		data.unbind(player.getUUID());
		level.removeBlock(pos, false);
		level.playSound(null, pos, FireclaySounds.KILN_CRACK, SoundSource.BLOCKS, 1.0f, 1.0f);
		Block.popResource(level, pos, new ItemStack(FireclayItems.KILN_SHARD, 2 + level.getRandom().nextInt(3)));
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return CeramicDrops.withBlockEntityComponents(super.getDrops(state, params), this, params);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReliquaryUrnBlockEntity urn) {
			urn.applyComponentsFromItemStack(stack);
			urn.setChanged();
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(FILLED) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SCULK_SOUL, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.0,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.03, 0.0);
		}
	}
}
