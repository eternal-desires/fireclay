package io.github.eternaldesires.fireclay.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * The rising {@code fireclay:kiln_ember} spark (design §3.12): lives 20–40 ticks, drifts up at about 0.02 blocks
 * per tick with a little sideways jitter, steps through its three frames (core, mid, edge) and fades out.
 * Owned by the systems feature.
 */
public class KilnEmberParticle extends SingleQuadParticle {
	/** Upward push per tick; with the friction below the ember settles near 0.02 blocks per tick. */
	private static final double LIFT = 0.00174;
	private static final double JITTER = 0.003;

	private final SpriteSet sprites;
	private final float startSize;

	protected KilnEmberParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
		super(level, x, y, z, xd, yd, zd, sprites.first());
		this.sprites = sprites;
		// The base constructor randomises the velocity; embers take the emitter's drift plus a small kick instead.
		this.xd = xd + (random.nextDouble() - 0.5) * 0.01;
		this.yd = yd + 0.01 + random.nextDouble() * 0.01;
		this.zd = zd + (random.nextDouble() - 0.5) * 0.01;
		this.gravity = 0.0F;
		this.friction = 0.92F;
		this.lifetime = 20 + random.nextInt(21);
		this.quadSize *= 0.6F;
		this.startSize = quadSize;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		xd += (random.nextDouble() - 0.5) * JITTER;
		zd += (random.nextDouble() - 0.5) * JITTER;
		yd += LIFT;
		super.tick();
		if (isAlive()) {
			setSpriteFromAge(sprites);
			float life = (float) age / lifetime;
			setAlpha(1.0F - life * life);
			quadSize = startSize * (1.0F - 0.4F * life);
		}
	}

	/** Embers glow: always full bright. */
	@Override
	public int getLightCoords(float partialTick) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	/** Registered with {@code ParticleProviderRegistry.getInstance().register(KILN_EMBER, Provider::new)}. */
	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
				double xd, double yd, double zd, RandomSource random) {
			return new KilnEmberParticle(level, x, y, z, xd, yd, zd, sprites);
		}
	}
}
