package io.github.eternaldesires.fireclay.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Unfired greenware (unfired_tile, _bell, _jar, _urn, _effigy). Shows "firing notes" from
 * {@code tooltip.fireclay.<id>.notes}: which glazes and heat windows apply. The notes are plain lang text rather than
 * a recipe lookup, because recipes are not synced to clients. Owned by the workshop feature.
 */
public class GreenwareItem extends Item {
	public GreenwareItem(Properties properties) {
		super(properties);
	}

	/** The lang key of this piece's firing notes, e.g. {@code tooltip.fireclay.unfired_jar.notes}. */
	public String getNotesKey() {
		return "tooltip.fireclay." + BuiltInRegistries.ITEM.getKey(this).getPath() + ".notes";
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		builder.accept(Component.translatable(getNotesKey()).withStyle(ChatFormatting.GRAY));
	}
}
