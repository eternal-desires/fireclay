package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.eternaldesires.fireclay.Fireclay;

/** Sound events (contract §5); the files and subtitles live in assets/fireclay/sounds.json. */
public final class FireclaySounds {
	public static final SoundEvent POTTERS_WHEEL_THROW = register("block.potters_wheel.throw");
	public static final SoundEvent BELLOWS_PUMP = register("block.bellows.pump");
	public static final SoundEvent KILN_ROAR = register("block.kiln.roar");
	public static final SoundEvent KILN_OVERHEAT = register("block.kiln.overheat");
	public static final SoundEvent KILN_CRACK = register("block.kiln.crack");
	public static final SoundEvent KILN_PING = register("block.kiln.ping");
	public static final SoundEvent KILN_MASTERWORK = register("block.kiln.masterwork");
	public static final SoundEvent SENTINEL_AWAKEN = register("entity.terracotta_sentinel.awaken");

	private FireclaySounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Fireclay.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
