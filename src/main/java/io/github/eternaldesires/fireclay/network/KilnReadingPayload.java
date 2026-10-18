package io.github.eternaldesires.fireclay.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.kiln.KilnMenu;

/**
 * S2C {@code fireclay:kiln_reading}: what a held Pyrometer sees (design §3.9).
 *
 * @param pos      the kiln
 * @param heat     current heat in degrees, rounded
 * @param cap      heat cap (750 unlined, 1000 lined)
 * @param window   {@code min << 16 | max} of the current recipe, 0 when there is no recipe
 * @param progress {@code progress << 16 | total} in ticks, 0 when there is no recipe
 * @param flags    the same bits as {@link KilnMenu}'s {@code FLAG_*} constants
 */
public record KilnReadingPayload(BlockPos pos, int heat, int cap, int window, int progress, int flags) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<KilnReadingPayload> TYPE = new CustomPacketPayload.Type<>(Fireclay.id("kiln_reading"));

	public static final StreamCodec<RegistryFriendlyByteBuf, KilnReadingPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, KilnReadingPayload::pos,
			ByteBufCodecs.VAR_INT, KilnReadingPayload::heat,
			ByteBufCodecs.VAR_INT, KilnReadingPayload::cap,
			ByteBufCodecs.INT, KilnReadingPayload::window,
			ByteBufCodecs.INT, KilnReadingPayload::progress,
			ByteBufCodecs.VAR_INT, KilnReadingPayload::flags,
			KilnReadingPayload::new);

	/** Packs the window and progress halves. All values must fit in 16 bits. */
	public static KilnReadingPayload of(BlockPos pos, int heat, int cap, int windowMin, int windowMax, int progress, int total, int flags) {
		return new KilnReadingPayload(pos, heat, cap, pack(windowMin, windowMax), pack(progress, total), flags);
	}

	private static int pack(int high, int low) {
		return (high & 0xFFFF) << 16 | (low & 0xFFFF);
	}

	public boolean hasRecipe() {
		return window != 0;
	}

	public int windowMin() {
		return window >>> 16;
	}

	public int windowMax() {
		return window & 0xFFFF;
	}

	public int progressTicks() {
		return progress >>> 16;
	}

	public int totalTicks() {
		return progress & 0xFFFF;
	}

	public boolean hasFlag(int flag) {
		return (flags & flag) != 0;
	}

	@Override
	public Type<KilnReadingPayload> type() {
		return TYPE;
	}
}
