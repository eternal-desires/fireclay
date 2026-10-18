package io.github.eternaldesires.fireclay.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.component.SentinelVitals;
import io.github.eternaldesires.fireclay.entity.SentinelRoster;
import io.github.eternaldesires.fireclay.entity.TerracottaSentinel;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayEntities;
import io.github.eternaldesires.fireclay.registry.FireclayGameRules;

/**
 * The fired effigy; using it on a block awakens a Terracotta Sentinel on that face (design §3.6). A Masterwork
 * effigy awakens a Masterwork Sentinel, and an effigy picked back up keeps the sentinel's health.
 */
public class SentinelEffigyItem extends Item {
	public SentinelEffigyItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Player player = context.getPlayer();

		if (!level.noCollision(FireclayEntities.TERRACOTTA_SENTINEL.getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(pos)))) {
			if (player != null && !level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.fireclay.sentinel.no_room"));
			}
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		SentinelRoster roster = SentinelRoster.get(serverLevel.getServer());
		if (player != null) {
			int max = FireclayGameRules.maxSentinelsPerPlayer(serverLevel);
			if (roster.count(player.getUUID()) >= max) {
				player.sendOverlayMessage(Component.translatable("message.fireclay.sentinel.limit", max));
				return InteractionResult.FAIL;
			}
		}

		TerracottaSentinel sentinel = FireclayEntities.TERRACOTTA_SENTINEL.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
		if (sentinel == null) {
			return InteractionResult.FAIL;
		}

		// Stand facing whoever woke it.
		float yaw = player != null ? Mth.wrapDegrees(player.getYRot() + 180.0f) : 0.0f;
		sentinel.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
		sentinel.setYHeadRot(yaw);
		sentinel.setYBodyRot(yaw);

		ItemStack stack = context.getItemInHand();
		SentinelVitals vitals = stack.get(FireclayComponents.SENTINEL_VITALS);
		sentinel.awaken(player, pos, Masterwork.get(stack), vitals != null ? vitals.health() : null);

		if (!serverLevel.addFreshEntity(sentinel)) {
			return InteractionResult.FAIL;
		}
		if (player != null) {
			roster.add(player.getUUID(), sentinel.getUUID());
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return Masterwork.isMasterwork(stack) || super.isFoil(stack);
	}
}
