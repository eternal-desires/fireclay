package io.github.eternaldesires.fireclay.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.eternaldesires.fireclay.client.hud.PyrometerHud;
import io.github.eternaldesires.fireclay.client.particle.KilnEmberParticle;
import io.github.eternaldesires.fireclay.network.KilnReadingPayload;
import io.github.eternaldesires.fireclay.registry.FireclayParticles;

/** Client hook of the systems feature, called from {@link FireclayClient}. */
public final class SystemsClient {
	private SystemsClient() {
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(FireclayParticles.KILN_EMBER, KilnEmberParticle.Provider::new);
		ClientPlayNetworking.registerGlobalReceiver(KilnReadingPayload.TYPE, PyrometerHud::onReading);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, PyrometerHud.ID, PyrometerHud::extractRenderState);
	}
}
