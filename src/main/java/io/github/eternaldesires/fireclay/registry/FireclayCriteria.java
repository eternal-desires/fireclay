package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;

/** Advancement criterion triggers. Vanilla's CriteriaTriggers has no public register, so use the registry. */
public final class FireclayCriteria {
	public static final PotterActionTrigger POTTER_ACTION = Registry.register(
			BuiltInRegistries.TRIGGER_TYPES, Fireclay.id("potter_action"), new PotterActionTrigger());

	private FireclayCriteria() {
	}

	public static void init() {
	}
}
