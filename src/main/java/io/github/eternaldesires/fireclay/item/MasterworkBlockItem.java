package io.github.eternaldesires.fireclay.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import io.github.eternaldesires.fireclay.component.Masterwork;

/**
 * Block item for pieces that can be fired as Masterworks (memory jar, sentry chime, vigil urn, ember tile,
 * reliquary urn). The glint comes from the {@code fireclay:masterwork} component. Owned by the ceramics feature.
 */
public class MasterworkBlockItem extends BlockItem {
	public MasterworkBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return Masterwork.isMasterwork(stack) || super.isFoil(stack);
	}
}
