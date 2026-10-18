package io.github.eternaldesires.fireclay.block;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The vanilla 3×3 dispenser menu with slots that refuse {@code #fireclay:storage_jar_blacklist}. The menu type stays
 * {@code generic_3x3}, so the client opens the vanilla dispenser screen and needs no registration.
 */
public class StorageJarMenu extends DispenserMenu {
	public StorageJarMenu(int containerId, Inventory inventory, Container jar) {
		super(containerId, inventory, jar);
	}

	@Override
	protected void add3x3GridSlots(Container container, int left, int top) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				addSlot(new JarSlot(container, column + row * 3, left + column * 18, top + row * 18));
			}
		}
	}

	private static final class JarSlot extends Slot {
		JarSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return StorageJarBlockEntity.canStore(stack);
		}
	}
}
