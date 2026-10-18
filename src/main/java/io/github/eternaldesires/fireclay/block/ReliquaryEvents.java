package io.github.eternaldesires.fireclay.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayGameRules;

/**
 * Death capture for Reliquary Urns (design §3.11, risk R12). {@code ALLOW_DEATH} runs before the totem check and
 * before any drops, so the inventory is moved into {@link ReliquaryData} there; death itself is always allowed.
 */
public final class ReliquaryEvents {
	/** Players whose belongings were caught, with the urn position for the respawn message. */
	private static final Map<UUID, BlockPos> PENDING_NOTICES = new ConcurrentHashMap<>();

	private ReliquaryEvents() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Player player) {
				capture(player);
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				notifyRespawn(newPlayer);
			}
		});
	}

	/**
	 * Moves the dying player's inventory into their bound urn if every condition holds: the game rule is on,
	 * keepInventory is off, the player is bound to an existing urn, and neither hand holds a totem.
	 *
	 * @return true if anything was captured
	 */
	public static boolean capture(Player player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!FireclayGameRules.reliquaryUrns(level) || level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return false;
		}
		if (player.getMainHandItem().has(DataComponents.DEATH_PROTECTION) || player.getOffhandItem().has(DataComponents.DEATH_PROTECTION)) {
			return false;
		}
		ReliquaryData data = ReliquaryData.get(level.getServer());
		Optional<ReliquaryData.Binding> binding = data.get(player.getUUID());
		if (binding.isEmpty()) {
			return false;
		}
		GlobalPos urnPos = binding.get().urn();
		ServerLevel urnLevel = level.getServer().getLevel(urnPos.dimension());
		if (urnLevel == null) {
			return false;
		}
		boolean urnLoaded = urnLevel.isLoaded(urnPos.pos());
		if (urnLoaded && !(urnLevel.getBlockEntity(urnPos.pos()) instanceof ReliquaryUrnBlockEntity)) {
			// The urn is gone (e.g. replaced while unloaded data lingered): drop the stale binding.
			data.unbind(player.getUUID());
			return false;
		}

		List<ItemStackWithSlot> held = new ArrayList<>(binding.get().held());
		int before = held.size();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			held.add(new ItemStackWithSlot(slot, inventory.removeItemNoUpdate(slot)));
		}
		if (held.size() == before) {
			return false;
		}
		inventory.setChanged();
		data.setHeld(player.getUUID(), held);
		if (urnLoaded && urnLevel.getBlockEntity(urnPos.pos()) instanceof ReliquaryUrnBlockEntity urn) {
			urn.syncWithData(urnLevel);
		}
		PENDING_NOTICES.put(player.getUUID(), urnPos.pos());
		return true;
	}

	private static void notifyRespawn(ServerPlayer player) {
		BlockPos pos = PENDING_NOTICES.remove(player.getUUID());
		if (pos != null) {
			player.sendSystemMessage(Component.translatable("message.fireclay.reliquary.rest", pos.getX(), pos.getY(), pos.getZ()));
			FireclayCriteria.POTTER_ACTION.trigger(player, PotterActionTrigger.RELIQUARY_SAVED);
		}
	}
}
