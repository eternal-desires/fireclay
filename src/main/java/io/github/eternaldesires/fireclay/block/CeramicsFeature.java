package io.github.eternaldesires.fireclay.block;

/** Ceramics feature hook (jars, chime, urns, ember tile), called once from {@code Fireclay#onInitialize}. */
public final class CeramicsFeature {
	private CeramicsFeature() {
	}

	public static void register() {
		ReliquaryEvents.register();
	}
}
