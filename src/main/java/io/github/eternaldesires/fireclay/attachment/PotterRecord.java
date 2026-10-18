package io.github.eternaldesires.fireclay.attachment;

import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.registry.FireclayAttachments;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;

/**
 * The {@code fireclay:potter_record} player attachment (persistent, copied on death). Immutable: every change
 * goes through {@link #update(Player, UnaryOperator)} or one of the {@code record*} helpers.
 */
public record PotterRecord(boolean primerGiven, int firings, int masterworks, int cracks) {
	public static final PotterRecord EMPTY = new PotterRecord(false, 0, 0, 0);
	/** Firings needed for the {@code hundred_fires} action. */
	public static final int HUNDRED_FIRES = 100;

	public static final Codec<PotterRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("primer_given", false).forGetter(PotterRecord::primerGiven),
			Codec.INT.optionalFieldOf("firings", 0).forGetter(PotterRecord::firings),
			Codec.INT.optionalFieldOf("masterworks", 0).forGetter(PotterRecord::masterworks),
			Codec.INT.optionalFieldOf("cracks", 0).forGetter(PotterRecord::cracks)
	).apply(instance, PotterRecord::new));

	public PotterRecord withPrimerGiven(boolean given) {
		return new PotterRecord(given, firings, masterworks, cracks);
	}

	public PotterRecord withFirings(int value) {
		return new PotterRecord(primerGiven, value, masterworks, cracks);
	}

	public PotterRecord withMasterworks(int value) {
		return new PotterRecord(primerGiven, firings, value, cracks);
	}

	public PotterRecord withCracks(int value) {
		return new PotterRecord(primerGiven, firings, masterworks, value);
	}

	/** The player's record, or {@link #EMPTY} (never null). */
	public static PotterRecord get(Player player) {
		return player.getAttachedOrElse(FireclayAttachments.POTTER_RECORD, EMPTY);
	}

	/** Applies {@code change} to the player's record and returns the new value. */
	public static PotterRecord update(Player player, UnaryOperator<PotterRecord> change) {
		PotterRecord updated = change.apply(get(player));
		player.setAttached(FireclayAttachments.POTTER_RECORD, updated);
		return updated;
	}

	public static void markPrimerGiven(Player player) {
		update(player, record -> record.withPrimerGiven(true));
	}

	/** Counts a completed firing and fires {@code hundred_fires} when the count reaches 100. */
	public static void recordFiring(ServerPlayer player) {
		PotterRecord updated = update(player, record -> record.withFirings(record.firings() + 1));
		if (updated.firings() == HUNDRED_FIRES) {
			FireclayCriteria.POTTER_ACTION.trigger(player, PotterActionTrigger.HUNDRED_FIRES);
		}
	}

	public static void recordMasterwork(ServerPlayer player) {
		update(player, record -> record.withMasterworks(record.masterworks() + 1));
	}

	public static void recordCrack(ServerPlayer player) {
		update(player, record -> record.withCracks(record.cracks() + 1));
	}
}
