package io.github.eternaldesires.fireclay.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.eternaldesires.fireclay.kiln.KilnBlock;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.registry.FireclayBlocks;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayGameRules;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/**
 * Kiln heat model and firing (design §2.2, §3.4.1): G2–G11, G17, G20. The simulation tests drive
 * {@link KilnBlockEntity#serverTick} in a loop inside one game tick, so they are exact and finish immediately.
 */
public class KilnGameTests {
	private static final BlockPos KILN = new BlockPos(2, 1, 2);

	private static KilnBlockEntity lined(GameTestHelper helper) {
		return TestSupport.kiln(helper, KILN, FireclayBlocks.KILN.defaultBlockState().setValue(KilnBlock.LINED, true));
	}

	/** G2: an unlined kiln on coal settles at 20 + 2.0/0.004 = 520. */
	@GameTest(maxTicks = 20)
	public void kilnCoalEquilibrium(GameTestHelper helper) {
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE, 64));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 8));
		TestSupport.tickKiln(helper, KILN, kiln, 3000);
		double heat = kiln.getHeat();
		helper.assertTrue(Math.abs(heat - 520) <= 8, "Coal equilibrium: H = " + heat + ", expected 520 ± 8");
		helper.assertTrue(kiln.isBurning(), "Kiln should still be burning after 3000 ticks");
		helper.assertBlockProperty(KILN, KilnBlock.HEAT, 2);
		helper.succeed();
	}

	/** G3: a lined kiln on oak logs settles at 20 + 1.2/0.0032 = 395. */
	@GameTest(maxTicks = 20)
	public void kilnLinedWoodEquilibrium(GameTestHelper helper) {
		KilnBlockEntity kiln = lined(helper);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE, 64));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.OAK_LOG, 16));
		TestSupport.tickKiln(helper, KILN, kiln, 3000);
		double heat = kiln.getHeat();
		helper.assertTrue(Math.abs(heat - 395) <= 8, "Lined wood equilibrium: H = " + heat + ", expected 395 ± 8");
		helper.assertValueEqual(kiln.getCap(), KilnBlockEntity.CAP_LINED, "lined cap");
		helper.succeed();
	}

	/** G4: one tile fired on coal gives four fired tiles and empties the greenware slot. */
	@GameTest(maxTicks = 20)
	public void kilnFiresTiles(GameTestHelper helper) {
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2));
		TestSupport.tickKiln(helper, KILN, kiln, 1500);
		ItemStack out = kiln.getItem(KilnBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(FireclayItems.FIRED_TILES) && out.getCount() == 4,
				"Output should be 4 fired tiles, was " + TestSupport.describe(out));
		helper.assertTrue(kiln.getItem(KilnBlockEntity.SLOT_GREENWARE).isEmpty(), "Greenware slot should be empty");
		helper.assertFalse(kiln.hasRecipe(), "No recipe should remain after the only tile fired");
		helper.succeed();
	}

	/** G5: a tile held above its 600° max cracks after the default 40-tick grace into 2–4 shards. */
	@GameTest(maxTicks = 20)
	public void kilnCracksWhenOver(GameTestHelper helper) {
		KilnBlockEntity kiln = lined(helper);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.BLAZE_ROD, 4));
		kiln.setHeat(700);
		TestSupport.tickKiln(helper, KILN, kiln, 200);
		ItemStack out = kiln.getItem(KilnBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(FireclayItems.KILN_SHARD) && out.getCount() >= 2 && out.getCount() <= 4,
				"Output should hold 2-4 kiln shards, was " + TestSupport.describe(out));
		helper.assertTrue(kiln.getItem(KilnBlockEntity.SLOT_GREENWARE).isEmpty(), "The cracked tile should be consumed");
		helper.assertValueEqual(kiln.getProgress(), 0, "progress after crack");
		helper.succeed();
	}

	/** G6: with kiln_crack_grace at 400, 100 ticks over the max do not crack the piece. */
	@GameTest(maxTicks = 20)
	public void kilnCrackGraceGamerule(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		GameRules rules = helper.getLevel().getGameRules();
		int old = rules.get(FireclayGameRules.KILN_CRACK_GRACE);
		// Game rules are server-wide; set and restore inside this one synchronous call so no other test sees it.
		rules.set(FireclayGameRules.KILN_CRACK_GRACE, 400, server);
		try {
			KilnBlockEntity kiln = lined(helper);
			kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
			kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.BLAZE_ROD, 4));
			kiln.setHeat(700);
			TestSupport.tickKiln(helper, KILN, kiln, 100);
			helper.assertTrue(kiln.getItem(KilnBlockEntity.SLOT_OUTPUT).isEmpty(),
					"Nothing should crack inside the grace, output " + TestSupport.describe(kiln.getItem(KilnBlockEntity.SLOT_OUTPUT)));
			helper.assertTrue(kiln.getItem(KilnBlockEntity.SLOT_GREENWARE).is(FireclayItems.UNFIRED_TILE), "The tile should survive");
			helper.assertValueEqual(kiln.getOver(), 100, "overheat counter");
		} finally {
			rules.set(FireclayGameRules.KILN_CRACK_GRACE, old, server);
		}
		helper.succeed();
	}

	/** G7: redstone into the back face opens the damper and cools the kiln fast; a side face does not. */
	@GameTest(maxTicks = 20)
	public void kilnDamperCools(GameTestHelper helper) {
		// facing = north (default), so the back face is south.
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		helper.setBlock(KILN.relative(Direction.SOUTH), Blocks.REDSTONE_BLOCK);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
		kiln.setHeat(700);
		TestSupport.tickKiln(helper, KILN, kiln, 125);
		helper.assertBlockProperty(KILN, KilnBlock.DAMPER, true);
		helper.assertTrue(kiln.isBurning(), "Coal should be burning");
		helper.assertTrue(kiln.getHeat() < 450, "Damper open: H = " + kiln.getHeat() + ", expected < 450");

		BlockPos side = new BlockPos(5, 1, 2);
		KilnBlockEntity sideKiln = TestSupport.kiln(helper, side);
		helper.setBlock(side.relative(Direction.EAST), Blocks.REDSTONE_BLOCK);
		sideKiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
		sideKiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
		sideKiln.setHeat(700);
		TestSupport.tickKiln(helper, side, sideKiln, 125);
		helper.assertBlockProperty(side, KilnBlock.DAMPER, false);
		helper.assertTrue(sideKiln.getHeat() > 450, "Side signal must not open the damper: H = " + sideKiln.getHeat());
		helper.succeed();
	}

	/** G9: the comparator reads the heat relative to the memory-jar window 540–680. */
	@GameTest(maxTicks = 20)
	public void comparatorIsWindowRelative(GameTestHelper helper) {
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_JAR));
		kiln.setItem(KilnBlockEntity.SLOT_GLAZE, new ItemStack(FireclayItems.LAPIS_SLIP));
		helper.assertTrue(kiln.hasRecipe(), "jar + lapis slip should match the memory jar recipe");
		helper.assertValueEqual(kiln.getWindowMin(), 540, "window min");
		helper.assertValueEqual(kiln.getWindowMax(), 680, "window max");
		kiln.setHeat(539);
		helper.assertValueEqual(kiln.getComparatorSignal(), 0, "signal at 539");
		kiln.setHeat(610);
		helper.assertValueEqual(kiln.getComparatorSignal(), 8, "signal at 610");
		kiln.setHeat(681);
		helper.assertValueEqual(kiln.getComparatorSignal(), 15, "signal at 681");

		// The block reports the same value to a real comparator once the kiln has ticked.
		TestSupport.tickKiln(helper, KILN, kiln, 1);
		int signal = kiln.getComparatorSignal();
		int analog = helper.getBlockState(KILN).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(KILN), Direction.NORTH);
		helper.assertValueEqual(analog, signal, "block analog output");
		helper.succeed();
	}

	/** G10: a memory jar fired at the centre of its window (coal block, 620) comes out a Masterwork. */
	@GameTest(maxTicks = 20)
	public void masterworkOnCenter(GameTestHelper helper) {
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_JAR));
		kiln.setItem(KilnBlockEntity.SLOT_GLAZE, new ItemStack(FireclayItems.LAPIS_SLIP));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL_BLOCK));
		kiln.setHeat(620);
		TestSupport.tickKiln(helper, KILN, kiln, 600);
		ItemStack out = kiln.getItem(KilnBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(FireclayItems.MEMORY_JAR), "Output should be a memory jar, was " + TestSupport.describe(out));
		helper.assertTrue(out.has(FireclayComponents.MASTERWORK), "A centred firing should give a Masterwork");
		helper.assertTrue(out.hasFoil(), "A Masterwork memory jar should have the enchantment glint");
		helper.assertTrue(kiln.getItem(KilnBlockEntity.SLOT_GLAZE).isEmpty(), "The glaze should be used up");
		helper.succeed();
	}

	/** G11: lava settles at 670, inside the window but above the 582–638 sweet spot, so no Masterwork. */
	@GameTest(maxTicks = 20)
	public void noMasterworkOffCenter(GameTestHelper helper) {
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_JAR));
		kiln.setItem(KilnBlockEntity.SLOT_GLAZE, new ItemStack(FireclayItems.LAPIS_SLIP));
		kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.LAVA_BUCKET));
		kiln.setHeat(670);
		TestSupport.tickKiln(helper, KILN, kiln, 600);
		ItemStack out = kiln.getItem(KilnBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(FireclayItems.MEMORY_JAR), "Output should be a memory jar, was " + TestSupport.describe(out));
		helper.assertFalse(out.has(FireclayComponents.MASTERWORK), "An off-centre firing must not give a Masterwork");
		helper.assertTrue(kiln.getItem(KilnBlockEntity.SLOT_FUEL).is(Items.BUCKET), "Lava should leave an empty bucket");
		helper.succeed();
	}

	/** G17: hoppers feed greenware from the top and fuel from the side; the bottom hopper takes the fired tiles. */
	@GameTest(maxTicks = 900)
	public void kilnHopperIo(GameTestHelper helper) {
		BlockPos kilnPos = new BlockPos(2, 2, 2);
		BlockPos top = kilnPos.above();
		BlockPos side = kilnPos.west();
		BlockPos bottom = kilnPos.below();
		helper.setBlock(kilnPos, FireclayBlocks.KILN.defaultBlockState());
		helper.setBlock(top, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(bottom, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.getBlockEntity(top, HopperBlockEntity.class).setItem(0, new ItemStack(FireclayItems.UNFIRED_TILE));
		helper.getBlockEntity(side, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 2));
		helper.succeedWhen(() -> helper.assertContainerContains(bottom, FireclayItems.FIRED_TILES));
	}

	/** G20: a kiln lining installs on use and comes back when the kiln is broken. */
	@GameTest(maxTicks = 20)
	public void kilnLiningInstallsAndDrops(GameTestHelper helper) {
		TestSupport.floor(helper);
		TestSupport.kiln(helper, KILN);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FireclayItems.KILN_LINING));
		helper.useBlock(KILN, player);
		helper.assertBlockProperty(KILN, KilnBlock.LINED, true);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "The lining should be used up");
		helper.assertValueEqual(helper.getBlockEntity(KILN, KilnBlockEntity.class).getCap(), KilnBlockEntity.CAP_LINED, "cap after lining");

		helper.getLevel().destroyBlock(helper.absolutePos(KILN), true);
		helper.assertItemEntityPresent(FireclayItems.KILN, KILN, 2.0);
		helper.assertItemEntityPresent(FireclayItems.KILN_LINING, KILN, 2.0);
		helper.succeed();
	}
}
