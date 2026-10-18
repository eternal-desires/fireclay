package io.github.eternaldesires.fireclay.command;

import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.entries.LootItem;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import io.github.eternaldesires.fireclay.registry.FireclayItems;

/** Systems feature hook (command, pyrometer sender, loot injection), called once from {@code Fireclay#onInitialize}. */
public final class SystemsFeature {
	/** Weight of the kiln shard among the trail ruins' common finds (each vanilla entry weighs 2). */
	private static final int KILN_SHARD_WEIGHT = 2;

	private SystemsFeature() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(FireclayCommand::register);
		ServerTickEvents.END_SERVER_TICK.register(PyrometerSender::onEndTick);
		registerLoot();
	}

	/**
	 * Adds a kiln shard to the trail ruins' common archaeology pool (design §3.18). Brushing yields exactly one item,
	 * so the entry joins the existing pool rather than adding a second one.
	 */
	private static void registerLoot() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (source.isBuiltin() && key.equals(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON)) {
				tableBuilder.modifyPools(pool -> pool.add(LootItem.lootTableItem(FireclayItems.KILN_SHARD).setWeight(KILN_SHARD_WEIGHT)));
			}
		});
	}
}
