package io.github.eternaldesires.fireclay.component;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import io.github.eternaldesires.fireclay.registry.FireclayComponents;

/** The {@code fireclay:sentinel_vitals} component: the health a picked-up Sentinel had. */
public record SentinelVitals(float health) implements TooltipProvider {
	public static final float BASE_MAX_HEALTH = 60.0f;
	public static final float MASTERWORK_MAX_HEALTH = 90.0f;

	/** Serialized as a bare float, e.g. {@code "fireclay:sentinel_vitals": 34.0}. */
	public static final Codec<SentinelVitals> CODEC = Codec.FLOAT.xmap(SentinelVitals::new, SentinelVitals::health);

	public static final StreamCodec<ByteBuf, SentinelVitals> STREAM_CODEC = ByteBufCodecs.FLOAT.map(SentinelVitals::new, SentinelVitals::health);

	/** Max health of a Sentinel, which depends on whether it is a Masterwork. */
	public static float maxHealth(boolean masterwork) {
		return masterwork ? MASTERWORK_MAX_HEALTH : BASE_MAX_HEALTH;
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		boolean masterwork = components.get(FireclayComponents.MASTERWORK) != null;
		int current = (int) Math.ceil(health);
		int max = (int) maxHealth(masterwork);
		tooltip.accept(Component.translatable("tooltip.fireclay.sentinel_vitals", current, max).withStyle(ChatFormatting.GRAY));
	}
}
