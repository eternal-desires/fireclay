package io.github.eternaldesires.fireclay.test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.kiln.KilnMenu;
import io.github.eternaldesires.fireclay.network.KilnReadingPayload;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/** Pyrometer payload and the /fireclay command (design §3.9, §3.16): G23 and a command check. */
public class SystemsGameTests {
	private static final BlockPos KILN = new BlockPos(2, 1, 2);

	/** G23: a kiln reading survives an encode/decode round trip, and packs the live kiln's window and progress. */
	@GameTest(maxTicks = 20)
	public void pyrometerPayloadCodec(GameTestHelper helper) {
		KilnReadingPayload sample = KilnReadingPayload.of(helper.absolutePos(KILN), 612, 1000, 540, 680, 123, 400,
				KilnMenu.FLAG_IN_SWEET | KilnMenu.FLAG_LINED);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			KilnReadingPayload.STREAM_CODEC.encode(buf, sample);
			KilnReadingPayload decoded = KilnReadingPayload.STREAM_CODEC.decode(buf);
			helper.assertValueEqual(decoded, sample, "decoded payload");
			helper.assertValueEqual(buf.readableBytes(), 0, "bytes left after decode");
		} finally {
			buf.release();
		}
		helper.assertValueEqual(sample.windowMin(), 540, "windowMin");
		helper.assertValueEqual(sample.windowMax(), 680, "windowMax");
		helper.assertValueEqual(sample.progressTicks(), 123, "progressTicks");
		helper.assertValueEqual(sample.totalTicks(), 400, "totalTicks");
		helper.assertTrue(sample.hasRecipe() && sample.hasFlag(KilnMenu.FLAG_LINED) && !sample.hasFlag(KilnMenu.FLAG_OVER), "flags");

		// A live reading of a memory-jar firing.
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		kiln.setItem(KilnBlockEntity.SLOT_GREENWARE, new ItemStack(FireclayItems.UNFIRED_JAR));
		kiln.setItem(KilnBlockEntity.SLOT_GLAZE, new ItemStack(FireclayItems.LAPIS_SLIP));
		kiln.setHeat(610);
		KilnReadingPayload reading = kiln.reading();
		helper.assertValueEqual(reading.pos(), helper.absolutePos(KILN), "reading pos");
		helper.assertValueEqual(reading.heat(), 610, "reading heat");
		helper.assertValueEqual(reading.cap(), KilnBlockEntity.CAP_UNLINED, "reading cap");
		helper.assertValueEqual(reading.windowMin(), 540, "reading window min");
		helper.assertValueEqual(reading.windowMax(), 680, "reading window max");
		helper.assertValueEqual(reading.totalTicks(), 400, "reading total");
		helper.assertTrue(reading.hasFlag(KilnMenu.FLAG_IN_SWEET), "610 is inside the 582-638 sweet spot");
		helper.succeed();
	}

	/** {@code /fireclay kiln <pos> heat <value>} sets the heat, clamped to the kiln's cap. */
	@GameTest(maxTicks = 20)
	public void fireclayCommandSetsKilnHeat(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		KilnBlockEntity kiln = TestSupport.kiln(helper, KILN);
		BlockPos abs = helper.absolutePos(KILN);
		String target = abs.getX() + " " + abs.getY() + " " + abs.getZ();

		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "fireclay kiln " + target + " heat 610");
		helper.assertTrue(Math.abs(kiln.getHeat() - 610) < 0.001, "Command heat 610: H = " + kiln.getHeat());
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "fireclay kiln " + target + " heat 1000");
		helper.assertTrue(Math.abs(kiln.getHeat() - KilnBlockEntity.CAP_UNLINED) < 0.001, "Command heat clamps to the cap: H = " + kiln.getHeat());
		helper.succeed();
	}
}
