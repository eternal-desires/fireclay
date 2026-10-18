package io.github.eternaldesires.fireclay.registry;

import java.util.function.UnaryOperator;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.component.ReliquaryCharges;
import io.github.eternaldesires.fireclay.component.SentinelVitals;
import io.github.eternaldesires.fireclay.component.StoredXp;

/** Item data components (contract §6). Every one is persistent and network-synchronized. */
public final class FireclayComponents {
	public static final DataComponentType<Masterwork> MASTERWORK = register("masterwork",
			builder -> builder.persistent(Masterwork.CODEC).networkSynchronized(Masterwork.STREAM_CODEC));
	public static final DataComponentType<StoredXp> STORED_XP = register("stored_xp",
			builder -> builder.persistent(StoredXp.CODEC).networkSynchronized(StoredXp.STREAM_CODEC));
	public static final DataComponentType<SentinelVitals> SENTINEL_VITALS = register("sentinel_vitals",
			builder -> builder.persistent(SentinelVitals.CODEC).networkSynchronized(SentinelVitals.STREAM_CODEC));
	public static final DataComponentType<ReliquaryCharges> RELIQUARY_CHARGES = register("reliquary_charges",
			builder -> builder.persistent(ReliquaryCharges.CODEC).networkSynchronized(ReliquaryCharges.STREAM_CODEC));

	private FireclayComponents() {
	}

	private static <T> DataComponentType<T> register(String path, UnaryOperator<DataComponentType.Builder<T>> builder) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fireclay.id(path), builder.apply(DataComponentType.builder()).build());
	}

	public static void init() {
		// Tooltip lines are drawn by the records themselves (they implement TooltipProvider).
		ItemComponentTooltipProviderRegistry.addLast(MASTERWORK);
		ItemComponentTooltipProviderRegistry.addLast(STORED_XP);
		ItemComponentTooltipProviderRegistry.addLast(SENTINEL_VITALS);
		ItemComponentTooltipProviderRegistry.addLast(RELIQUARY_CHARGES);
	}
}
