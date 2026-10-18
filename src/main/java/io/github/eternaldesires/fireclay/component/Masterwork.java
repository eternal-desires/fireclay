package io.github.eternaldesires.fireclay.component;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import io.github.eternaldesires.fireclay.registry.FireclayComponents;

/**
 * The {@code fireclay:masterwork} component: a piece fired in the sweet spot, optionally signed with the
 * potter's mark (the stoker's name). Blockstate-only pieces lose the name and come back as {@link #UNSIGNED}.
 */
public record Masterwork(Optional<String> potter) implements TooltipProvider {
	public static final Masterwork UNSIGNED = new Masterwork(Optional.empty());

	public static final Codec<Masterwork> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("potter").forGetter(Masterwork::potter)
	).apply(instance, Masterwork::new));

	public static final StreamCodec<ByteBuf, Masterwork> STREAM_CODEC =
			ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(Masterwork::new, Masterwork::potter);

	/** A masterwork signed by {@code potter}, or unsigned when the name is unknown. */
	public static Masterwork signedBy(@Nullable String potter) {
		return potter == null || potter.isEmpty() ? UNSIGNED : new Masterwork(Optional.of(potter));
	}

	public static boolean isMasterwork(ItemStack stack) {
		return stack.has(FireclayComponents.MASTERWORK);
	}

	public static Optional<Masterwork> get(ItemStack stack) {
		return Optional.ofNullable(stack.get(FireclayComponents.MASTERWORK));
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		tooltip.accept(Component.translatable("tooltip.fireclay.masterwork").withStyle(ChatFormatting.GOLD));
		potter.ifPresent(name -> tooltip.accept(Component.translatable("tooltip.fireclay.masterwork.potter", name)
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
	}
}
