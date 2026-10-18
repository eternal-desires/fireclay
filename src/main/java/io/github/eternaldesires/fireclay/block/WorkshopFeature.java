package io.github.eternaldesires.fireclay.block;

/** Workshop feature hook (wheel, bellows, greenware, primer), called once from {@code Fireclay#onInitialize}. */
public final class WorkshopFeature {
	private WorkshopFeature() {
	}

	/**
	 * Nothing to wire up: the wheel and bellows act through their block overrides, the greenware tooltip through
	 * {@code GreenwareItem#appendHoverText}, and the Primer is handed out by the wheel's {@code setPlacedBy}.
	 */
	public static void register() {
	}
}
