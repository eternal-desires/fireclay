package io.github.eternaldesires.fireclay.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.registry.FireclayBlocks;

/** Shared helpers for the server gametests. All positions are structure-relative unless named {@code abs}. */
final class TestSupport {
	private TestSupport() {
	}

	/** A stone floor at y = 0 across the default 8×8 structure (it is all air). */
	static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Places a kiln and returns its block entity. */
	static KilnBlockEntity kiln(GameTestHelper helper, BlockPos pos, BlockState state) {
		helper.setBlock(pos, state);
		return helper.getBlockEntity(pos, KilnBlockEntity.class);
	}

	static KilnBlockEntity kiln(GameTestHelper helper, BlockPos pos) {
		return kiln(helper, pos, FireclayBlocks.KILN.defaultBlockState());
	}

	/**
	 * Runs {@code ticks} kiln ticks synchronously, the way Fabric's FurnaceGameTest drives a furnace. The position is
	 * absolute because the kiln writes its own blockstate (heat stage, damper).
	 */
	static void tickKiln(GameTestHelper helper, BlockPos pos, KilnBlockEntity kiln, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		for (int i = 0; i < ticks; i++) {
			KilnBlockEntity.serverTick(level, abs, level.getBlockState(abs), kiln);
		}
	}

	/** Total count of {@code item} in item entities inside the test area. */
	static int countItemEntities(GameTestHelper helper, Item item) {
		int total = 0;
		for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM)) {
			ItemStack stack = entity.getItem();
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** The first item entity holding {@code item} within {@code radius} of {@code pos}. */
	static ItemStack findDrop(GameTestHelper helper, Item item, BlockPos pos, double radius) {
		return helper.getEntities(EntityTypes.ITEM, pos, radius).stream()
				.map(ItemEntity::getItem)
				.filter(stack -> stack.is(item))
				.findFirst()
				.orElseThrow(() -> helper.assertionException(Component.literal("No dropped " + item + " near " + pos)));
	}

	static String describe(ItemStack stack) {
		return stack.isEmpty() ? "empty" : stack.getCount() + "x " + stack.getItem();
	}
}
