package io.github.eternaldesires.fireclay.client;

import net.minecraft.client.renderer.entity.EntityRenderers;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.eternaldesires.fireclay.client.entity.SentinelModel;
import io.github.eternaldesires.fireclay.client.entity.SentinelRenderer;
import io.github.eternaldesires.fireclay.registry.FireclayEntities;

/** Client hook of the sentinel feature, called from {@link FireclayClient}. */
public final class SentinelClient {
	private SentinelClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(SentinelModel.LAYER, SentinelModel::createBodyLayer);
		EntityRenderers.register(FireclayEntities.TERRACOTTA_SENTINEL, SentinelRenderer::new);
	}
}
