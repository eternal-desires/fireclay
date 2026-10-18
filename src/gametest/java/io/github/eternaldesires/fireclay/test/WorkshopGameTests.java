package io.github.eternaldesires.fireclay.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.eternaldesires.fireclay.attachment.PotterRecord;
import io.github.eternaldesires.fireclay.block.BellowsBlock;
import io.github.eternaldesires.fireclay.block.PottersWheelBlock;
import io.github.eternaldesires.fireclay.block.PottersWheelBlockEntity;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.registry.FireclayBlocks;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/** Bellows and potter's wheel (design §3.2): G8, G12, plus the first-wheel primer. */
public class WorkshopGameTests {
	/** Waits past the bellows' scheduled un-press. */
	private static final int UNPRESS_WAIT = BellowsBlock.PRESS_TICKS + 2;

	/** G8: a pump adds 35° and costs 20 burn ticks; a pressed bellows refuses; a redstone rising edge pumps once. */
	@GameTest(maxTicks = 80)
	public void bellowsPumpsKiln(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos kilnPos = new BlockPos(3, 1, 2);
		BlockPos bellowsPos = kilnPos.west();
		BlockPos redstone = bellowsPos.west();
		BlockPos bellowsAbs = helper.absolutePos(bellowsPos);

		KilnBlockEntity kiln = TestSupport.kiln(helper, kilnPos);
		helper.setBlock(bellowsPos, FireclayBlocks.BELLOWS.defaultBlockState().setValue(BellowsBlock.FACING, Direction.EAST));
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
		kiln.setHeat(400);
		TestSupport.tickKiln(helper, kilnPos, kiln, 1);
		helper.assertTrue(kiln.isBurning(), "The kiln should have lit its coal");

		double heat = kiln.getHeat();
		int burn = kiln.getBurn();
		helper.assertTrue(BellowsBlock.pump(level, bellowsAbs, null), "First pump should go through");
		helper.assertTrue(Math.abs(kiln.getHeat() - (heat + KilnBlockEntity.PUMP_HEAT)) <= 1.0,
				"Pump: H " + heat + " -> " + kiln.getHeat() + ", expected +35");
		helper.assertValueEqual(kiln.getBurn(), burn - KilnBlockEntity.PUMP_BURN_COST, "burn after pump");
		helper.assertBlockProperty(bellowsPos, BellowsBlock.PRESSED, true);

		double pumped = kiln.getHeat();
		helper.assertFalse(BellowsBlock.pump(level, bellowsAbs, null), "A pressed bellows must refuse a second pump");
		helper.assertValueEqual(kiln.getHeat(), pumped, "heat after refused pump");

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.startSequence()
				.thenExecuteAfter(UNPRESS_WAIT, () -> {
					helper.assertBlockProperty(bellowsPos, BellowsBlock.PRESSED, false);
					// A manual pump makes the player the kiln's stoker.
					helper.assertTrue(BellowsBlock.pump(level, bellowsAbs, player), "Manual pump after the bellows sprang back");
					helper.assertValueEqual(kiln.getStokerName(), player.getName().getString(), "stoker after manual pump");
				})
				.thenExecuteAfter(UNPRESS_WAIT, () -> {
					helper.assertBlockProperty(bellowsPos, BellowsBlock.PRESSED, false);
					double before = kiln.getHeat();
					helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
					helper.assertBlockProperty(bellowsPos, BellowsBlock.POWERED, true);
					helper.assertBlockProperty(bellowsPos, BellowsBlock.PRESSED, true);
					helper.assertTrue(Math.abs(kiln.getHeat() - (before + KilnBlockEntity.PUMP_HEAT)) <= 1.0,
							"Redstone edge: H " + before + " -> " + kiln.getHeat() + ", expected +35");
				})
				.thenExecuteAfter(UNPRESS_WAIT, () -> {
					// Still powered, but no new edge: the bellows stays open.
					helper.assertBlockProperty(bellowsPos, BellowsBlock.POWERED, true);
					helper.assertBlockProperty(bellowsPos, BellowsBlock.PRESSED, false);
				})
				.thenSucceed();
	}

	/** G12: four clay balls, sneak-cycle to Jar, five throws (waiting out each spin) → an unfired jar pops out. */
	@GameTest(maxTicks = 120)
	public void wheelThrowsJar(GameTestHelper helper) {
		TestSupport.floor(helper);
		BlockPos wheelPos = new BlockPos(3, 1, 3);
		helper.setBlock(wheelPos, FireclayBlocks.POTTERS_WHEEL);
		PottersWheelBlockEntity wheel = helper.getBlockEntity(wheelPos, PottersWheelBlockEntity.class);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLAY_BALL, 4));
		for (int i = 0; i < 4; i++) {
			helper.useBlock(wheelPos, player);
		}
		helper.assertValueEqual(wheel.getCount(), 4, "loaded clay");
		helper.assertBlockProperty(wheelPos, PottersWheelBlock.CLAY, 2);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "All four balls should be on the wheel");

		player.setShiftKeyDown(true);
		helper.useBlock(wheelPos, player); // Tile -> Bell
		helper.useBlock(wheelPos, player); // Bell -> Jar
		player.setShiftKeyDown(false);
		helper.assertValueEqual(wheel.getShape(), PottersWheelBlockEntity.Shape.JAR, "chosen shape");

		Runnable throwOnce = () -> helper.useBlock(wheelPos, player);
		throwOnce.run();
		helper.assertValueEqual(wheel.getThrows(), 1, "throws after the first throw");
		helper.assertBlockProperty(wheelPos, PottersWheelBlock.SPINNING, true);
		int wait = PottersWheelBlock.SPIN_TICKS + 1;
		helper.startSequence()
				.thenExecuteAfter(wait, throwOnce)
				.thenExecuteAfter(wait, throwOnce)
				.thenExecuteAfter(wait, throwOnce)
				.thenExecuteAfter(wait, () -> {
					helper.assertValueEqual(wheel.getThrows(), 4, "throws before the last");
					throwOnce.run();
					helper.assertItemEntityPresent(FireclayItems.UNFIRED_JAR, wheelPos.above(), 2.0);
					helper.assertValueEqual(wheel.getCount(), 0, "clay left on the wheel");
					helper.assertValueEqual(wheel.getThrows(), 0, "throws after finishing");
					helper.assertBlockProperty(wheelPos, PottersWheelBlock.CLAY, 0);
				})
				.thenSucceed();
	}

	/** The first wheel a player places gives The Potter's Primer, once (design §2.5). */
	@GameTest(maxTicks = 20)
	public void firstWheelGivesPrimer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.assertFalse(PotterRecord.get(player).primerGiven(), "A new player has no primer yet");

		BlockPos first = new BlockPos(1, 1, 1);
		BlockPos second = new BlockPos(4, 1, 1);
		for (BlockPos pos : new BlockPos[] {first, second}) {
			helper.setBlock(pos, FireclayBlocks.POTTERS_WHEEL);
			BlockState state = helper.getBlockState(pos);
			FireclayBlocks.POTTERS_WHEEL.setPlacedBy(level, helper.absolutePos(pos), state, player, new ItemStack(FireclayItems.POTTERS_WHEEL));
		}
		helper.assertTrue(PotterRecord.get(player).primerGiven(), "The record should note the primer");
		helper.assertValueEqual(countBooks(player.getInventory()), 1, "written books after two wheels");
		helper.succeed();
	}

	private static int countBooks(Inventory inventory) {
		int books = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(Items.WRITTEN_BOOK)) {
				books += inventory.getItem(slot).getCount();
			}
		}
		return books;
	}
}
