package io.github.eternaldesires.fireclay.client;

import net.minecraft.client.gui.screens.MenuScreens;

import io.github.eternaldesires.fireclay.client.screen.KilnScreen;
import io.github.eternaldesires.fireclay.registry.FireclayMenus;

/** Client hook of the kiln feature, called from {@link FireclayClient}. */
public final class KilnClient {
	private KilnClient() {
	}

	public static void register() {
		MenuScreens.register(FireclayMenus.KILN, KilnScreen::new);
	}
}
