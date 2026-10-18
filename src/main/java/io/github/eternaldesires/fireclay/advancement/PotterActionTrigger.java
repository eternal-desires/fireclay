package io.github.eternaldesires.fireclay.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The single parameterized criterion {@code fireclay:potter_action} behind the whole advancement tab.
 *
 * <p>JSON conditions: {@code {"action": "fired", "item": {"items": "fireclay:memory_jar"}, "masterwork": true}};
 * only {@code action} is required.
 */
public class PotterActionTrigger extends SimpleCriterionTrigger<PotterActionTrigger.TriggerInstance> {
	public static final String THREW = "threw";
	public static final String FIRED = "fired";
	public static final String CRACKED = "cracked";
	public static final String PUMPED = "pumped";
	public static final String MASTERWORK = "masterwork";
	public static final String THERMOSTAT = "thermostat";
	public static final String LINED = "lined";
	public static final String WHITE_HEAT = "white_heat";
	public static final String AWAKENED = "awakened";
	public static final String PYROMETER_READ = "pyrometer_read";
	public static final String EMBER_BAKED = "ember_baked";
	public static final String RELIQUARY_SAVED = "reliquary_saved";
	public static final String HUNDRED_FIRES = "hundred_fires";

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	/** Fires {@code action} with no item and masterwork = false. */
	public void trigger(ServerPlayer player, String action) {
		trigger(player, action, ItemStack.EMPTY, false);
	}

	/**
	 * Fires {@code action}. {@code item} is the piece involved (or {@link ItemStack#EMPTY}); {@code masterwork}
	 * says whether that piece (or the awakened Sentinel) is a Masterwork.
	 */
	public void trigger(ServerPlayer player, String action, ItemStack item, boolean masterwork) {
		this.trigger(player, instance -> instance.matches(action, item, masterwork));
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player, String action,
			Optional<ItemPredicate> item, Optional<Boolean> masterwork) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
				Codec.STRING.fieldOf("action").forGetter(TriggerInstance::action),
				ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item),
				Codec.BOOL.optionalFieldOf("masterwork").forGetter(TriggerInstance::masterwork)
		).apply(instance, TriggerInstance::new));

		public boolean matches(String action, ItemStack stack, boolean isMasterwork) {
			return this.action.equals(action)
					&& item.map(predicate -> !stack.isEmpty() && predicate.test(stack)).orElse(true)
					&& masterwork.map(required -> required == isMasterwork).orElse(true);
		}
	}
}
