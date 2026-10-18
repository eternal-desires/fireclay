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

/** The {@code fireclay:reliquary_charges} component: how many more deaths a Reliquary Urn item can catch. */
public record ReliquaryCharges(int charges) implements TooltipProvider {
	/** Serialized as a bare positive int, e.g. {@code "fireclay:reliquary_charges": 2}. */
	public static final Codec<ReliquaryCharges> CODEC = ExtraCodecs.POSITIVE_INT.xmap(ReliquaryCharges::new, ReliquaryCharges::charges);

	public static final StreamCodec<ByteBuf, ReliquaryCharges> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ReliquaryCharges::new, ReliquaryCharges::charges);

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		tooltip.accept(Component.translatable("tooltip.fireclay.reliquary_charges", charges).withStyle(ChatFormatting.DARK_AQUA));
	}
}
