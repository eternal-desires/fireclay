package io.github.eternaldesires.fireclay.registry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import io.github.eternaldesires.fireclay.network.KilnReadingPayload;

/** Payload types. Registered in common code so both the server and the client know them. */
public final class FireclayNetworking {
	private FireclayNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(KilnReadingPayload.TYPE, KilnReadingPayload.STREAM_CODEC);
	}
}
