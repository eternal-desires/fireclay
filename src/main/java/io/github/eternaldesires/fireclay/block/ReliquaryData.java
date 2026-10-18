package io.github.eternaldesires.fireclay.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.eternaldesires.fireclay.Fireclay;

/**
 * Saved data {@code fireclay:fireclay_reliquaries}: each player's bound Reliquary Urn and the belongings it holds
 * (design §3.11). Kept on the server-wide storage because urns and deaths can be in different dimensions.
 */
public final class ReliquaryData extends SavedData {
	/** One player's urn and, after a death, the captured inventory slots. */
	public record Binding(GlobalPos urn, List<ItemStackWithSlot> held) {
		public static final Codec<Binding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				GlobalPos.CODEC.fieldOf("urn").forGetter(Binding::urn),
				ItemStackWithSlot.CODEC.listOf().optionalFieldOf("held", List.of()).forGetter(Binding::held)
		).apply(instance, Binding::new));

		public Binding {
			held = List.copyOf(held);
		}

		public boolean isFilled() {
			return !held.isEmpty();
		}
	}

	public static final Codec<ReliquaryData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Binding.CODEC)
			.xmap(ReliquaryData::new, data -> data.bindings);

	public static final SavedDataType<ReliquaryData> TYPE =
			new SavedDataType<>(Fireclay.id("fireclay_reliquaries"), ReliquaryData::new, CODEC, null);

	private final Map<UUID, Binding> bindings;

	public ReliquaryData() {
		this(Map.of());
	}

	private ReliquaryData(Map<UUID, Binding> bindings) {
		this.bindings = new HashMap<>(bindings);
	}

	public static ReliquaryData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Optional<Binding> get(UUID owner) {
		return Optional.ofNullable(bindings.get(owner));
	}

	/** True if {@code owner} is bound to the urn at {@code urn}. */
	public boolean isBoundTo(UUID owner, GlobalPos urn) {
		Binding binding = bindings.get(owner);
		return binding != null && binding.urn().equals(urn);
	}

	/** Binds {@code owner} to {@code urn}, replacing any earlier (empty) binding. */
	public void bind(UUID owner, GlobalPos urn) {
		Binding old = bindings.get(owner);
		bindings.put(owner, new Binding(urn, old != null && old.urn().equals(urn) ? old.held() : List.of()));
		setDirty();
	}

	public void setHeld(UUID owner, List<ItemStackWithSlot> held) {
		Binding binding = bindings.get(owner);
		if (binding != null) {
			bindings.put(owner, new Binding(binding.urn(), held));
			setDirty();
		}
	}

	public void unbind(UUID owner) {
		if (bindings.remove(owner) != null) {
			setDirty();
		}
	}
}
