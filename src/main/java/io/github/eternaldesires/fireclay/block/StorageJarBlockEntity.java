package io.github.eternaldesires.fireclay.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/**
 * Nine slots behind a vanilla 3×3 dispenser menu. The contents travel with the item as {@code minecraft:container}
 * (and a rename as {@code minecraft:custom_name}); {@link BaseContainerBlockEntity} already maps both components in
 * its implicit-component hooks. Owned by the ceramics feature.
 */
public class StorageJarBlockEntity extends BaseContainerBlockEntity {
	public static final int SLOT_COUNT = 9;

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

	public StorageJarBlockEntity(BlockPos pos, BlockState state) {
		super(FireclayBlockEntities.STORAGE_JAR, pos, state);
	}

	/** Shulker boxes and other jars stay out (no nesting). */
	public static boolean canStore(ItemStack stack) {
		return !stack.is(FireclayTags.Items.STORAGE_JAR_BLACKLIST);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.fireclay.storage_jar");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new StorageJarMenu(containerId, inventory, this);
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return canStore(stack);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	/** Keep the contents for the dropped item instead of spilling them (as shulker boxes do). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
	}
}
