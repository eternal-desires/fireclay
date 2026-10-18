package io.github.eternaldesires.fireclay.kiln;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** Recipe input for {@code fireclay:firing}: the greenware slot and the (possibly empty) glaze slot. */
public record FiringInput(ItemStack greenware, ItemStack glaze) implements RecipeInput {
	@Override
	public ItemStack getItem(int index) {
		return switch (index) {
			case 0 -> greenware;
			case 1 -> glaze;
			default -> throw new IllegalArgumentException("FiringInput has no slot " + index);
		};
	}

	@Override
	public int size() {
		return 2;
	}
}
