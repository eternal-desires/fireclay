package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.entity.TerracottaSentinel;

/** Entity types (contract §3). */
public final class FireclayEntities {
	public static final ResourceKey<EntityType<?>> TERRACOTTA_SENTINEL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Fireclay.id("terracotta_sentinel"));

	public static final EntityType<TerracottaSentinel> TERRACOTTA_SENTINEL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, TERRACOTTA_SENTINEL_KEY,
			EntityType.Builder.<TerracottaSentinel>of(TerracottaSentinel::new, MobCategory.MISC)
					.sized(0.7f, 2.0f)
					.fireImmune()
					.clientTrackingRange(10)
					.build(TERRACOTTA_SENTINEL_KEY));

	private FireclayEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TERRACOTTA_SENTINEL, TerracottaSentinel.createAttributes());
	}
}
