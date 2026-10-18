package io.github.eternaldesires.fireclay.entity;

/**
 * Sentinel feature hook, called once from {@code Fireclay#onInitialize} after every registry is loaded.
 * The entity type and its default attributes are registered by {@code FireclayEntities}; awakening, the
 * per-player cap ({@link SentinelRoster}) and pickup live on the entity and the effigy item, so no global
 * events are needed.
 */
public final class SentinelFeature {
	private SentinelFeature() {
	}

	public static void register() {
	}
}
