package io.github.eternaldesires.fireclay.client.hud;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.kiln.KilnMenu;
import io.github.eternaldesires.fireclay.network.KilnReadingPayload;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/**
 * The Pyrometer heat bar above the hotbar (design §3.9): a 120×6 gauge on a 0–1000° scale showing the firing
 * window, its sweet spot and a needle at the current heat, with a "612° · 63% fired" label above it.
 * Owned by the systems feature.
 */
public final class PyrometerHud {
	/** HUD element id (contract §6). */
	public static final Identifier ID = Fireclay.id("pyrometer");

	private static final int WIDTH = 120;
	private static final int HEIGHT = 6;
	/** Distance of the bar's top edge from the bottom of the screen. */
	private static final int BOTTOM_OFFSET = 62;
	private static final int SCALE = 1000;
	/** Readings older than this many client ticks are not drawn (the server sends one every 4 ticks). */
	private static final int MAX_AGE = 10;

	private static final int COLOR_BACKGROUND = 0xFF2A1A14;
	private static final int COLOR_WINDOW = 0xFF3FA34D;
	private static final int COLOR_SWEET = 0xFF8CE07A;
	private static final int COLOR_CAP = 0xFF5A3A2C;
	private static final int COLOR_NEEDLE = 0xFFFFFFFF;
	private static final int COLOR_OVER = 0xFFE0401A;
	private static final int COLOR_TEXT = 0xFFFFFFFF;

	private static @Nullable KilnReadingPayload lastReading;
	/** The local player's {@code tickCount} when {@link #lastReading} arrived. */
	private static int lastReadingTick;

	private PyrometerHud() {
	}

	/** {@code kiln_reading} receiver; runs on the render thread. */
	public static void onReading(KilnReadingPayload payload, ClientPlayNetworking.Context context) {
		lastReading = payload;
		lastReadingTick = context.player().tickCount;
	}

	/** The HUD element body (matches {@code HudElement#extractRenderState}). */
	public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		KilnReadingPayload reading = lastReading;
		if (player == null || reading == null || !player.isHolding(FireclayItems.PYROMETER)) {
			return;
		}
		int age = player.tickCount - lastReadingTick;
		if (age < 0 || age > MAX_AGE) {
			return;
		}

		int left = (graphics.guiWidth() - WIDTH) / 2;
		int top = graphics.guiHeight() - BOTTOM_OFFSET;
		int bottom = top + HEIGHT;

		graphics.fill(left, top, left + WIDTH, bottom, COLOR_BACKGROUND);
		// Above the cap the kiln cannot go; shade that stretch so an unlined kiln reads as "tops out at 750".
		if (reading.cap() < SCALE) {
			graphics.fill(toX(left, reading.cap()), top, left + WIDTH, bottom, COLOR_CAP);
		}
		if (reading.hasRecipe()) {
			int min = reading.windowMin();
			int max = reading.windowMax();
			graphics.fill(toX(left, min), top, Math.max(toX(left, max), toX(left, min) + 1), bottom, COLOR_WINDOW);
			// The sweet spot is the middle 40% of the window (design §2.2).
			double width = max - min;
			int sweetMin = (int) Math.ceil(min + 0.3 * width);
			int sweetMax = (int) Math.floor(max - 0.3 * width);
			if (sweetMax > sweetMin) {
				graphics.fill(toX(left, sweetMin), top, toX(left, sweetMax), bottom, COLOR_SWEET);
			}
		}

		int needle = Math.min(toX(left, reading.heat()), left + WIDTH - 1);
		graphics.fill(needle, top - 1, needle + 1, bottom + 1, COLOR_NEEDLE);
		// Flash a red border while the piece is overheating.
		if (reading.hasFlag(KilnMenu.FLAG_OVER) && (player.tickCount / 3) % 2 == 0) {
			graphics.outline(left - 1, top - 1, WIDTH + 2, HEIGHT + 2, COLOR_OVER);
		}

		Component label;
		if (reading.hasRecipe() && reading.totalTicks() > 0) {
			int percent = Math.min(100, reading.progressTicks() * 100 / reading.totalTicks());
			label = Component.translatable("hud.fireclay.pyrometer", reading.heat(), percent);
		} else {
			label = Component.translatable("hud.fireclay.pyrometer.idle", reading.heat());
		}
		graphics.text(minecraft.font, label, (graphics.guiWidth() - minecraft.font.width(label)) / 2, top - 11, COLOR_TEXT, true);
	}

	/** Whether a reading arrived recently enough to be drawn; used by the client gametest. */
	public static boolean hasFreshReading() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && lastReading != null && player.tickCount - lastReadingTick <= MAX_AGE;
	}

	/** Screen x of a temperature on the 0–1000° bar. */
	private static int toX(int left, int degrees) {
		int clamped = Math.max(0, Math.min(SCALE, degrees));
		return left + clamped * WIDTH / SCALE;
	}
}
