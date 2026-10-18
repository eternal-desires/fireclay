package io.github.eternaldesires.fireclay.component;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/** The {@code fireclay:stored_xp} component: experience points kept by a broken Memory Jar. */
public record StoredXp(int points) implements TooltipProvider {
	public static final StoredXp EMPTY = new StoredXp(0);

	/** Serialized as a bare non-negative int, e.g. {@code "fireclay:stored_xp": 300}. */
	public static final Codec<StoredXp> CODEC = ExtraCodecs.NON_NEGATIVE_INT.xmap(StoredXp::new, StoredXp::points);

	public static final StreamCodec<ByteBuf, StoredXp> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(StoredXp::new, StoredXp::points);

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		tooltip.accept(Component.translatable("tooltip.fireclay.stored_xp", points).withStyle(ChatFormatting.GREEN));
	}
}
