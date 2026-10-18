package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import io.github.eternaldesires.fireclay.Fireclay;

/** Particle types. Registered on both sides; the client provider is registered in SystemsClient. */
public final class FireclayParticles {
	public static final SimpleParticleType KILN_EMBER = Registry.register(
			BuiltInRegistries.PARTICLE_TYPE, Fireclay.id("kiln_ember"), FabricParticleTypes.simple());

	private FireclayParticles() {
	}

	public static void init() {
	}
}
