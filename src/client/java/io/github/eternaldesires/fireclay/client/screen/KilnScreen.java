package io.github.eternaldesires.fireclay.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.kiln.KilnMenu;

/**
 * The kiln GUI (design §3.5, contract §4): flame and arrow sprites from the sheet, plus a vertical heat gauge drawn
 * at runtime with the firing window, the sweet spot, the current-heat needle and the overheat/masterwork cues.
 */
public class KilnScreen extends AbstractContainerScreen<KilnMenu> {
	public static final Identifier TEXTURE = Fireclay.id("textures/gui/container/kiln.png");

	private static final int SHEET_SIZE = 256;
	private static final int FLAME_U = 176, FLAME_V = 0, FLAME_X = 50, FLAME_Y = 36, FLAME_SIZE = 14;
	private static final int ARROW_U = 176, ARROW_V = 14, ARROW_X = 79, ARROW_Y = 34, ARROW_W = 24, ARROW_H = 17;

	// Gauge well (inside the 12x62 frame at 151,13) and its 0..1000 degree scale.
	private static final int WELL_X = 152, WELL_Y = 14, WELL_W = 10, WELL_H = 60;
	private static final int FRAME_X = 151, FRAME_Y = 13, FRAME_W = 12, FRAME_H = 62;
	private static final double GAUGE_MAX = 1000.0;
	private static final int NEEDLE_X = 148;
	private static final int STAR_X = 150, STAR_Y = 76;

	private static final int COLOR_WELL = 0xFF2A1A14;
	private static final int COLOR_HATCH = 0xFF3A2A24;
	private static final int COLOR_WINDOW = 0xC03FA34D;
	private static final int COLOR_SWEET = 0xFF8CE07A;
	private static final int COLOR_NEEDLE = 0xFFFFFFFF;
	private static final int COLOR_OVER = 0xFFE0401A;
	private static final int COLOR_STAR = 0xFFF2C94C;
	private static final int COLOR_LABEL = 0xFF404040;
	private static final int COLOR_LABEL_OVER = 0xFFB02E26;

	/** A 5x5 star, one string per row. */
	private static final String[] STAR = {
			"..#..",
			".###.",
			"#####",
			".###.",
			".#.#."};

	public KilnScreen(KilnMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET_SIZE, SHEET_SIZE);
		drawFlame(graphics);
		drawArrow(graphics);
		drawGauge(graphics);
	}

	/** The flame burns down from the top as the current fuel item is used up. */
	private void drawFlame(GuiGraphicsExtractor graphics) {
		int burn = menu.getBurn();
		int burnMax = menu.getBurnMax();
		if (burn <= 0 || burnMax <= 0) {
			return;
		}
		int h = Mth.clamp(Mth.ceil(FLAME_SIZE * (double) burn / burnMax), 1, FLAME_SIZE);
		int skip = FLAME_SIZE - h;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + FLAME_X, topPos + FLAME_Y + skip,
				FLAME_U, FLAME_V + skip, FLAME_SIZE, h, SHEET_SIZE, SHEET_SIZE);
	}

	/** The arrow fills left to right with the firing progress. */
	private void drawArrow(GuiGraphicsExtractor graphics) {
		int total = menu.getTotal();
		int progress = menu.getProgress();
		if (total <= 0 || progress <= 0) {
			return;
		}
		int w = Mth.clamp(ARROW_W * progress / total, 0, ARROW_W);
		if (w > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + ARROW_X, topPos + ARROW_Y,
					ARROW_U, ARROW_V, w, ARROW_H, SHEET_SIZE, SHEET_SIZE);
		}
	}

	private void drawGauge(GuiGraphicsExtractor graphics) {
		int x0 = leftPos + WELL_X;
		int x1 = x0 + WELL_W;
		int top = topPos + WELL_Y;
		int bottom = top + WELL_H;
		int heat = menu.getHeat();
		boolean over = menu.hasFlag(KilnMenu.FLAG_OVER);

		graphics.fill(x0, top, x1, bottom, COLOR_WELL);

		// Above the cap the kiln cannot go: diagonal hatching.
		int capY = gaugeY(menu.getCap(), bottom);
		for (int y = top; y < capY; y++) {
			for (int x = x0; x < x1; x++) {
				if (((x - x0) + (y - top)) % 4 < 2) {
					graphics.fill(x, y, x + 1, y + 1, COLOR_HATCH);
				}
			}
		}

		if (menu.hasRecipe()) {
			int min = menu.getWindowMin();
			int max = menu.getWindowMax();
			double width = max - min;
			fillBand(graphics, x0, x1, gaugeY(max, bottom), gaugeY(min, bottom), COLOR_WINDOW);
			fillBand(graphics, x0, x1, gaugeY(max - 0.3 * width, bottom), gaugeY(min + 0.3 * width, bottom), COLOR_SWEET);
		}

		// Needle: a 2 px line across the well plus a small triangle pointing at it from the left.
		int needleY = Mth.clamp(gaugeY(heat, bottom), top + 1, bottom - 1);
		graphics.fill(x0, needleY - 1, x1, needleY + 1, COLOR_NEEDLE);
		int nx = leftPos + NEEDLE_X;
		graphics.fill(nx, needleY - 2, nx + 1, needleY + 2, COLOR_NEEDLE);
		graphics.fill(nx + 1, needleY - 1, nx + 2, needleY + 1, COLOR_NEEDLE);

		if (over && (Util.getMillis() / 150) % 2 == 0) {
			graphics.outline(leftPos + FRAME_X, topPos + FRAME_Y, FRAME_W, FRAME_H, COLOR_OVER);
		}
		if (menu.hasFlag(KilnMenu.FLAG_MASTERWORK_ON_TRACK)) {
			drawStar(graphics, leftPos + STAR_X, topPos + STAR_Y);
		}

		// Current heat, right-aligned just left of the star, under the needle side of the gauge.
		Component label = Component.translatable("container.fireclay.kiln.heat", heat);
		graphics.text(font, label, leftPos + NEEDLE_X - font.width(label), topPos + STAR_Y, over ? COLOR_LABEL_OVER : COLOR_LABEL, false);
	}

	/** Screen y of a heat value on the 0..1000 scale. */
	private static int gaugeY(double heat, int bottom) {
		return bottom - (int) Math.round(Mth.clamp(heat, 0.0, GAUGE_MAX) * WELL_H / GAUGE_MAX);
	}

	private static void fillBand(GuiGraphicsExtractor graphics, int x0, int x1, int yTop, int yBottom, int color) {
		graphics.fill(x0, yTop, x1, Math.max(yBottom, yTop + 1), color);
	}

	private static void drawStar(GuiGraphicsExtractor graphics, int x, int y) {
		for (int row = 0; row < STAR.length; row++) {
			for (int col = 0; col < STAR[row].length(); col++) {
				if (STAR[row].charAt(col) == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, COLOR_STAR);
				}
			}
		}
	}
}
