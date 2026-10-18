package io.github.eternaldesires.fireclay.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.entity.TerracottaSentinel;

/** Renderer of the Terracotta Sentinel (design §3.6): two skins chosen by the masterwork flag, shadow 0.5. */
public class SentinelRenderer extends MobRenderer<TerracottaSentinel, SentinelRenderState, SentinelModel> {
	public static final Identifier TEXTURE = Fireclay.id("textures/entity/terracotta_sentinel.png");
	public static final Identifier TEXTURE_MASTERWORK = Fireclay.id("textures/entity/terracotta_sentinel_masterwork.png");

	public SentinelRenderer(EntityRendererProvider.Context context) {
		super(context, new SentinelModel(context.bakeLayer(SentinelModel.LAYER)), 0.5f);
	}

	@Override
	public SentinelRenderState createRenderState() {
		return new SentinelRenderState();
	}

	@Override
	public void extractRenderState(TerracottaSentinel entity, SentinelRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.masterwork = entity.isMasterwork();
		int awaken = entity.getAwaken();
		state.awaken = awaken > 0 ? Math.max(0.0f, awaken - partialTick) / TerracottaSentinel.AWAKEN_TICKS : 0.0f;
		int attack = entity.getAttackAnimationTick();
		state.attackAnim = attack > 0 ? attack - partialTick : 0.0f;
	}

	/** The figure trembles while it cracks free of the kiln glaze. */
	@Override
	protected boolean isShaking(SentinelRenderState state) {
		return state.awaken > 0.0f || super.isShaking(state);
	}

	@Override
	public Identifier getTextureLocation(SentinelRenderState state) {
		return state.masterwork ? TEXTURE_MASTERWORK : TEXTURE;
	}
}
