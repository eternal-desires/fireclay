package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.kiln.KilnMenu;

/** Menu types. The kiln uses a plain MenuType; its live numbers travel through ContainerData. */
public final class FireclayMenus {
	public static final MenuType<KilnMenu> KILN = Registry.register(
			BuiltInRegistries.MENU, Fireclay.id("kiln"), new MenuType<>(KilnMenu::new, FeatureFlags.VANILLA_SET));

	private FireclayMenus() {
	}

	public static void init() {
	}
}
