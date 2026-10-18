package io.github.eternaldesires.fireclay.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.eternaldesires.fireclay.Fireclay;

/**
 * The {@code fireclay_sentinels} saved data: which sentinels each player has awakened, for the
 * {@code fireclay:max_sentinels_per_player} game rule. Server-wide, so sentinels in every dimension count.
 * Entries are added on awakening and removed when a sentinel dies or is picked back up.
 */
public final class SentinelRoster extends SavedData {
	private static final Codec<SentinelRoster> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC_SET)
			.xmap(SentinelRoster::new, roster -> roster.sentinels);

	public static final SavedDataType<SentinelRoster> TYPE =
			new SavedDataType<>(Fireclay.id("fireclay_sentinels"), SentinelRoster::new, CODEC, null);

	private final Map<UUID, Set<UUID>> sentinels = new HashMap<>();

	private SentinelRoster() {
	}

	private SentinelRoster(Map<UUID, Set<UUID>> sentinels) {
		// Copy into mutable collections; decoded ones may be immutable.
		sentinels.forEach((owner, ids) -> this.sentinels.put(owner, new HashSet<>(ids)));
	}

	public static SentinelRoster get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** How many living sentinels {@code owner} currently has. */
	public int count(UUID owner) {
		Set<UUID> ids = this.sentinels.get(owner);
		return ids == null ? 0 : ids.size();
	}

	public void add(UUID owner, UUID sentinel) {
		if (this.sentinels.computeIfAbsent(owner, key -> new HashSet<>()).add(sentinel)) {
			this.setDirty();
		}
	}

	public void remove(UUID owner, UUID sentinel) {
		Set<UUID> ids = this.sentinels.get(owner);
		if (ids != null && ids.remove(sentinel)) {
			if (ids.isEmpty()) {
				this.sentinels.remove(owner);
			}
			this.setDirty();
		}
	}
}
