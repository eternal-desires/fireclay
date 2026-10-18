package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import io.github.eternaldesires.fireclay.Fireclay;

/** Tag keys (contract §6). The JSON lives in data/fireclay/tags/{item,entity_type}/. */
public final class FireclayTags {
	public static final class Items {
		public static final TagKey<Item> GREENWARE = item("greenware");
		public static final TagKey<Item> GLAZES = item("glazes");
		/** Union of every kiln fuel tier. */
		public static final TagKey<Item> KILN_FUELS = item("kiln_fuels");
		public static final TagKey<Item> KILN_FUEL_KINDLING = item("kiln_fuel/kindling");
		public static final TagKey<Item> KILN_FUEL_WOOD = item("kiln_fuel/wood");
		public static final TagKey<Item> KILN_FUEL_COAL = item("kiln_fuel/coal");
		public static final TagKey<Item> KILN_FUEL_COAL_BLOCK = item("kiln_fuel/coal_block");
		public static final TagKey<Item> KILN_FUEL_LAVA = item("kiln_fuel/lava");
		public static final TagKey<Item> KILN_FUEL_BLAZE = item("kiln_fuel/blaze");
		public static final TagKey<Item> MASTERWORK_ELIGIBLE = item("masterwork_eligible");
		public static final TagKey<Item> STORAGE_JAR_BLACKLIST = item("storage_jar_blacklist");

		private Items() {
		}

		private static TagKey<Item> item(String path) {
			return TagKey.create(Registries.ITEM, Fireclay.id(path));
		}
	}

	public static final class EntityTypes {
		/** Never pushed by a Vigil Urn. */
		public static final TagKey<EntityType<?>> VIGIL_IMMUNE = entity("vigil_immune");
		/** Never targeted by a Terracotta Sentinel. */
		public static final TagKey<EntityType<?>> SENTINEL_IGNORES = entity("sentinel_ignores");

		private EntityTypes() {
		}

		private static TagKey<EntityType<?>> entity(String path) {
			return TagKey.create(Registries.ENTITY_TYPE, Fireclay.id(path));
		}
	}

	private FireclayTags() {
	}
}
