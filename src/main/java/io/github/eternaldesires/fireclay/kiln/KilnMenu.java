package io.github.eternaldesires.fireclay.kiln;

import java.util.function.Predicate;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.eternaldesires.fireclay.registry.FireclayMenus;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/**
 * The kiln menu (design §3.5). Slot and ContainerData layout are a cross-feature contract (docs/api.md);
 * the kiln feature owns the rest of this class.
 */
public class KilnMenu extends AbstractContainerMenu {
	// ContainerData indices; every value is kept within 0..32767 because data slots sync as shorts.
	public static final int DATA_HEAT = 0;
	public static final int DATA_CAP = 1;
	public static final int DATA_WINDOW_MIN = 2;
	public static final int DATA_WINDOW_MAX = 3;
	public static final int DATA_PROGRESS = 4;
	public static final int DATA_TOTAL = 5;
	public static final int DATA_BURN = 6;
	public static final int DATA_BURN_MAX = 7;
	public static final int DATA_FLAGS = 8;
	public static final int DATA_COUNT = 9;

	// Bits of DATA_FLAGS (also used by KilnReadingPayload#flags).
	public static final int FLAG_OVER = 1;
	public static final int FLAG_IN_SWEET = 2;
	public static final int FLAG_DAMPER = 4;
	public static final int FLAG_LINED = 8;
	public static final int FLAG_MASTERWORK_ON_TRACK = 16;

	// Menu slot indices: 0..3 are the kiln, then 27 inventory slots and 9 hotbar slots.
	public static final int KILN_SLOTS = KilnBlockEntity.SLOT_COUNT;
	public static final int INVENTORY_START = KILN_SLOTS;
	public static final int INVENTORY_END = INVENTORY_START + 36;
	private static final int HOTBAR_START = INVENTORY_END - 9;

	private final Container container;
	private final ContainerData data;

	/** Client constructor (MenuType factory). */
	public KilnMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(KilnBlockEntity.SLOT_COUNT), new SimpleContainerData(DATA_COUNT));
	}

	/** Server constructor, called from {@link KilnBlockEntity}. */
	public KilnMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(FireclayMenus.KILN, containerId);
		checkContainerSize(container, KilnBlockEntity.SLOT_COUNT);
		checkContainerDataCount(data, DATA_COUNT);
		this.container = container;
		this.data = data;
		container.startOpen(inventory.player);
		addSlot(new FilteredSlot(container, KilnBlockEntity.SLOT_GREENWARE, 38, 17, KilnMenu::isGreenware));
		addSlot(new FilteredSlot(container, KilnBlockEntity.SLOT_GLAZE, 62, 17, KilnMenu::isGlaze));
		addSlot(new FilteredSlot(container, KilnBlockEntity.SLOT_FUEL, 50, 53, KilnMenu::isFuelOrBucket));
		addSlot(new FilteredSlot(container, KilnBlockEntity.SLOT_OUTPUT, 116, 35, stack -> false));
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	/** Shift-click: kiln slots go to the inventory; inventory items are routed by tag (design §3.5). */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (slotIndex < KILN_SLOTS) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
			if (slotIndex == KilnBlockEntity.SLOT_OUTPUT) {
				slot.onQuickCraft(stack, moved);
			}
		} else if (isGreenware(stack)) {
			if (!moveItemStackTo(stack, KilnBlockEntity.SLOT_GREENWARE, KilnBlockEntity.SLOT_GREENWARE + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (isGlaze(stack)) {
			if (!moveItemStackTo(stack, KilnBlockEntity.SLOT_GLAZE, KilnBlockEntity.SLOT_GLAZE + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (KilnBlockEntity.isFuel(stack)) {
			if (!moveItemStackTo(stack, KilnBlockEntity.SLOT_FUEL, KilnBlockEntity.SLOT_FUEL + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex < HOTBAR_START) {
			if (!moveItemStackTo(stack, HOTBAR_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == moved.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return moved;
	}

	private static boolean isGreenware(ItemStack stack) {
		return stack.is(FireclayTags.Items.GREENWARE);
	}

	private static boolean isGlaze(ItemStack stack) {
		return stack.is(FireclayTags.Items.GLAZES);
	}

	/** The fuel slot also takes back the empty bucket a lava firing leaves. */
	private static boolean isFuelOrBucket(ItemStack stack) {
		return KilnBlockEntity.isFuel(stack) || stack.is(Items.BUCKET);
	}

	/** A kiln slot with an item filter; the output slot uses one that refuses everything. */
	private static final class FilteredSlot extends Slot {
		private final Predicate<ItemStack> filter;

		FilteredSlot(Container container, int index, int x, int y, Predicate<ItemStack> filter) {
			super(container, index, x, y);
			this.filter = filter;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return filter.test(stack);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	public int getData(int index) {
		return data.get(index);
	}

	public int getHeat() {
		return data.get(DATA_HEAT);
	}

	public int getCap() {
		return data.get(DATA_CAP);
	}

	public int getWindowMin() {
		return data.get(DATA_WINDOW_MIN);
	}

	public int getWindowMax() {
		return data.get(DATA_WINDOW_MAX);
	}

	/** True when a recipe matches the current slots (the window is non-empty). */
	public boolean hasRecipe() {
		return getWindowMax() > 0;
	}

	public int getProgress() {
		return data.get(DATA_PROGRESS);
	}

	public int getTotal() {
		return data.get(DATA_TOTAL);
	}

	public int getBurn() {
		return data.get(DATA_BURN);
	}

	public int getBurnMax() {
		return data.get(DATA_BURN_MAX);
	}

	public int getFlags() {
		return data.get(DATA_FLAGS);
	}

	public boolean hasFlag(int flag) {
		return (getFlags() & flag) != 0;
	}
}
