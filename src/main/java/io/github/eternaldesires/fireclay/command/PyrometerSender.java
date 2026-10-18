package io.github.eternaldesires.fireclay.command;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.network.KilnReadingPayload;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/**
 * Server half of the Pyrometer (design §3.9): every few ticks, each player holding one in either hand who looks
 * at a kiln within {@link #RANGE} blocks receives a {@link KilnReadingPayload}.
 */
public final class PyrometerSender {
	/** Ticks between readings (5 per second). */
	public static final int INTERVAL = 4;
	/** Raycast reach in blocks. */
	public static final double RANGE = 6.0;

	/** Players who received a reading on the previous sample; used to fire {@code pyrometer_read} once per glance. */
	private static Set<UUID> reading = new HashSet<>();

	private PyrometerSender() {
	}

	/** {@code ServerTickEvents.END_SERVER_TICK} callback. */
	public static void onEndTick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		Set<UUID> nowReading = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isHolding(FireclayItems.PYROMETER)) {
				continue;
			}
			@Nullable KilnBlockEntity kiln = targetedKiln(player);
			if (kiln == null || !ServerPlayNetworking.canSend(player, KilnReadingPayload.TYPE)) {
				continue;
			}
			ServerPlayNetworking.send(player, kiln.reading());
			nowReading.add(player.getUUID());
			if (!reading.contains(player.getUUID())) {
				FireclayCriteria.POTTER_ACTION.trigger(player, PotterActionTrigger.PYROMETER_READ);
			}
		}
		// Replacing the set each sample also forgets players who logged out.
		reading = nowReading;
	}

	/** The kiln the player is looking at within range, or null. */
	public static @Nullable KilnBlockEntity targetedKiln(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return player.level().getBlockEntity(hit.getBlockPos()) instanceof KilnBlockEntity kiln ? kiln : null;
	}
}
