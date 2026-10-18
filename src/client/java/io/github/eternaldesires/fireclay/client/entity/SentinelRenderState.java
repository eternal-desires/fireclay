package io.github.eternaldesires.fireclay.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of a Terracotta Sentinel, filled by {@link SentinelRenderer#extractRenderState}. */
public class SentinelRenderState extends LivingEntityRenderState {
	/** Awakening progress left: 1 at the start of the sequence, 0 once awake. */
	public float awaken;
	public boolean masterwork;
	/** Remaining attack-swing ticks (10 → 0), interpolated. */
	public float attackAnim;
}
