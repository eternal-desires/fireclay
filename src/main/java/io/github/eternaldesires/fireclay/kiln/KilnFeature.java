package io.github.eternaldesires.fireclay.kiln;

/**
 * Kiln feature hook, called once from {@code Fireclay#onInitialize} after every registry is loaded.
 *
 * <p>The kiln needs no events: its block, block entity, menu and recipe type are registered by the foundation
 * registries, and hoppers and Fabric's transfer API reach it through {@link net.minecraft.world.WorldlyContainer}.
 */
public final class KilnFeature {
	private KilnFeature() {
	}

	public static void register() {
	}
}
