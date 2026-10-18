package io.github.eternaldesires.fireclay.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;

/**
 * Moves block-entity data onto dropped ceramics. The loot tables are plain "drop self" tables (design §3.18), so the
 * components (jar contents, stored XP, masterwork, charges) are added here from {@link BlockEntity#collectComponents()},
 * which gathers each block entity's {@code collectImplicitComponents}. Placement reads them back through
 * {@link BlockEntity#applyComponentsFromItemStack(ItemStack)}.
 */
final class CeramicDrops {
	private CeramicDrops() {
	}

	/** Writes the block entity's components onto every drop of {@code block}'s own item. */
	static List<ItemStack> withBlockEntityComponents(List<ItemStack> drops, Block block, LootParams.Builder params) {
		BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity != null) {
			for (ItemStack drop : drops) {
				if (drop.is(block.asItem())) {
					drop.applyComponents(blockEntity.collectComponents());
				}
			}
		}
		return drops;
	}

	/**
	 * Blockstate-only pieces (chime, vigil urn, ember tile) keep only the masterwork flag, so their drops get
	 * {@link Masterwork#UNSIGNED} back.
	 */
	static List<ItemStack> withMasterwork(List<ItemStack> drops, Block block, boolean masterwork) {
		if (masterwork) {
			for (ItemStack drop : drops) {
				if (drop.is(block.asItem())) {
					drop.set(FireclayComponents.MASTERWORK, Masterwork.UNSIGNED);
				}
			}
		}
		return drops;
	}

	/** Creative players get no loot; like a shulker box, a jar that holds something still drops itself. */
	static void dropForCreative(Level level, BlockPos pos, Block block, BlockEntity blockEntity) {
		ItemStack stack = new ItemStack(block);
		stack.applyComponents(blockEntity.collectComponents());
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}
}
