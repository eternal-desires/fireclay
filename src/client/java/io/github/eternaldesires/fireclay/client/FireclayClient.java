package io.github.eternaldesires.fireclay.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Every client registration lives in a feature hook so each feature owns its own:
 * {@link KilnClient} (kiln screen), {@link SentinelClient} (renderer, model layer) and {@link SystemsClient}
 * (particle provider, pyrometer HUD, kiln_reading receiver). No render-layer setup is needed: all block
 * textures are opaque.
 */
public class FireclayClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KilnClient.register();
		SentinelClient.register();
		SystemsClient.register();
	}
}
