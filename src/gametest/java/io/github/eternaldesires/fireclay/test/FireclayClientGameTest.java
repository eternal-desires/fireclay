package io.github.eternaldesires.fireclay.test;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.eternaldesires.fireclay.client.hud.PyrometerHud;
import io.github.eternaldesires.fireclay.client.screen.KilnScreen;
import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.entity.TerracottaSentinel;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.registry.FireclayEntities;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/**
 * Client gametest C1–C5 (design §5): builds a showcase in a fresh creative flat world and takes screenshots for
 * visual review. Pass/fail is only "no exception"; the screenshots land in build/run/clientGameTest/screenshots/.
 *
 * <p>Server commands run as the server source, not the player, so every position is absolute, computed from where
 * the player spawned. The scene lies south (+z) of the spawn; the camera looks south at it.
 */
@SuppressWarnings("UnstableApiUsage")
public class FireclayClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());
			int x = spawn.getX();
			int y = spawn.getY();
			int z = spawn.getZ();

			BlockPos showcaseKiln = new BlockPos(x, y, z + 6);
			BlockPos workKiln = new BlockPos(x - 3, y, z + 6);

			// C1: the showcase, by day.
			server.runCommand("time set 1000");
			server.runCommand(fill(x - 5, y - 1, z + 1, x + 5, y - 1, z + 8, "fireclay:lumen_tiles"));
			server.runCommand(fill(x - 4, y - 1, z + 2, x + 4, y - 1, z + 7, "fireclay:fired_tiles"));
			server.runCommand(setblock(showcaseKiln, "fireclay:kiln[facing=north,lined=true]"));
			server.runCommand(setblock(showcaseKiln.west(), "fireclay:bellows[facing=east]"));
			server.runCommand(setblock(new BlockPos(x + 2, y, z + 6), "fireclay:potters_wheel[clay=4,fireclay=true]"));
			server.runCommand(setblock(workKiln, "fireclay:kiln[facing=north]"));
			server.runCommand(setblock(new BlockPos(x - 4, y, z + 6), "fireclay:fireclay_bricks"));
			server.runCommand(setblock(new BlockPos(x + 4, y, z + 6), "fireclay:fireclay_brick_wall"));
			server.runCommand(setblock(new BlockPos(x - 3, y, z + 4), "fireclay:storage_jar"));
			server.runCommand(setblock(new BlockPos(x - 2, y, z + 4), "fireclay:memory_jar[facing=north,fill=3]"));
			server.runCommand(setblock(new BlockPos(x - 1, y, z + 4), "fireclay:sentry_chime[face=floor,facing=north]"));
			server.runCommand(setblock(new BlockPos(x + 1, y, z + 4), "fireclay:vigil_urn"));
			server.runCommand(setblock(new BlockPos(x + 2, y, z + 4), "fireclay:reliquary_urn"));
			server.runCommand(setblock(new BlockPos(x + 3, y - 1, z + 4), "fireclay:ember_tile"));
			server.runCommand(setblock(new BlockPos(x + 4, y, z + 4), "fireclay:fired_tile_stairs[facing=west]"));

			server.runOnServer(minecraftServer -> {
				ServerLevel level = singleplayer.getConnection().getServerLevel();
				// A white-hot showcase kiln: blaze rods in a lined kiln settle near 957°. The tile overheats and cracks,
				// but a lit blaze rod keeps burning for 2400 ticks, far longer than this test.
				if (level.getBlockEntity(showcaseKiln) instanceof KilnBlockEntity kiln) {
					kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_TILE));
					kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.BLAZE_ROD, 4));
					kiln.setHeat(950);
				}
				spawnSentinel(level, new BlockPos(x + 3, y, z + 5), Optional.empty());
				spawnSentinel(level, new BlockPos(x - 4, y, z + 4), Optional.of(Masterwork.signedBy("Player0")));
			});

			camera(server, x + 0.5, y, z - 2.5, 0.0f, 22.0f);
			context.waitTick();
			singleplayer.getConnection().waitForClientboundPackets();
			singleplayer.getConnection().waitForChunksRender();
			// Let both sentinels finish their 60-tick awakening and the kiln reach white heat.
			context.waitTicks(80);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("fireclay-showcase-day");

			// C2: night, lit by the white-hot kiln (light 15), lumen tiles and the vigil urn.
			server.runCommand("time set 18000");
			context.waitTicks(20);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("fireclay-showcase-night");

			// C3: the kiln screen on a memory-jar firing at 610° on a coal block (window, sweet band, needle, flame, arrow).
			server.runOnServer(minecraftServer -> {
				ServerLevel level = singleplayer.getConnection().getServerLevel();
				if (level.getBlockEntity(workKiln) instanceof KilnBlockEntity kiln) {
					kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_JAR));
					kiln.setItem(KilnBlockEntity.SLOT_GLAZE, new ItemStack(FireclayItems.LAPIS_SLIP));
					kiln.setItem(KilnBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL_BLOCK, 4));
				}
			});
			server.runCommand("fireclay kiln %d %d %d heat 610".formatted(workKiln.getX(), workKiln.getY(), workKiln.getZ()));
			context.waitTicks(60); // some progress for the arrow
			server.runOnServer(minecraftServer -> {
				ServerLevel level = singleplayer.getConnection().getServerLevel();
				if (level.getBlockEntity(workKiln) instanceof KilnBlockEntity kiln) {
					singleplayer.getConnection().getServerPlayer().openMenu(kiln);
				}
			});
			context.waitForScreen(KilnScreen.class);
			context.waitTicks(10);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("fireclay-kiln-screen");
			context.runOnClient(client -> client.player.closeContainer());
			context.waitForScreen(null);

			// C4: the Pyrometer HUD while looking at the working kiln from close by.
			server.runCommand("time set 1000");
			holdInMainHand(singleplayer, server, FireclayItems.PYROMETER);
			camera(server, workKiln.getX() + 0.5, y, workKiln.getZ() - 2.5, 0.0f, 20.0f);
			context.waitTick();
			context.getInput().lookAt(workKiln);
			context.waitFor(client -> PyrometerHud.hasFreshReading(), 100);
			context.waitTicks(5);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("fireclay-pyrometer-hud");

			// C5: the Sentinel Awakening, caught halfway through its 60-tick sequence.
			holdInMainHand(singleplayer, server, FireclayItems.SENTINEL_EFFIGY);
			camera(server, x + 0.5, y, z + 0.5, 0.0f, 45.0f);
			context.waitTick();
			context.getInput().lookAt(new BlockPos(x, y - 1, z + 2));
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			camera(server, x + 0.5, y, z - 2.5, 0.0f, 15.0f);
			context.waitTicks(26);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("fireclay-awakening");
		}
	}

	private static void spawnSentinel(ServerLevel level, BlockPos pos, Optional<Masterwork> masterwork) {
		TerracottaSentinel sentinel = FireclayEntities.TERRACOTTA_SENTINEL.create(level, EntitySpawnReason.COMMAND);
		if (sentinel == null) {
			throw new AssertionError("Could not create a Terracotta Sentinel");
		}
		// Yaw 180 faces north, towards the camera.
		sentinel.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
		sentinel.setYHeadRot(180.0f);
		sentinel.setYBodyRot(180.0f);
		sentinel.awaken(null, pos, masterwork, null);
		sentinel.setNoAi(true);
		level.addFreshEntity(sentinel);
	}

	private static void holdInMainHand(TestSingleplayerContext singleplayer, TestServerContext server, Item item) {
		server.runOnServer(minecraftServer ->
				singleplayer.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item)));
		singleplayer.getConnection().waitForClientboundPackets();
	}

	private static void camera(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		// Locale.ROOT: the command parser needs a dot as the decimal separator.
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static String setblock(BlockPos pos, String state) {
		return "setblock %d %d %d %s".formatted(pos.getX(), pos.getY(), pos.getZ(), state);
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill %d %d %d %d %d %d %s".formatted(x1, y1, z1, x2, y2, z2, block);
	}
}
