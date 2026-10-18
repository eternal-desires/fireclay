package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.primer.PottersPrimer;

/** The {@code fireclay:fireclay} creative tab, listing every item in progression order. */
public final class FireclayCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Fireclay.id("fireclay"));

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.fireclay"))
			.icon(() -> new ItemStack(FireclayItems.KILN))
			.displayItems((parameters, output) -> {
				// Workshop
				output.accept(FireclayItems.POTTERS_WHEEL);
				output.accept(FireclayItems.KILN);
				output.accept(FireclayItems.BELLOWS);
				output.accept(FireclayItems.PYROMETER);
				output.accept(PottersPrimer.create());
				// Greenware and slips
				output.accept(FireclayItems.UNFIRED_TILE);
				output.accept(FireclayItems.UNFIRED_JAR);
				output.accept(FireclayItems.UNFIRED_BELL);
				output.accept(FireclayItems.UNFIRED_URN);
				output.accept(FireclayItems.UNFIRED_EFFIGY);
				output.accept(FireclayItems.LAPIS_SLIP);
				output.accept(FireclayItems.GLOWINK_SLIP);
				output.accept(FireclayItems.AMETHYST_SLIP);
				output.accept(FireclayItems.BLAZE_SLIP);
				output.accept(FireclayItems.ECHO_SLIP);
				// Earthenware
				output.accept(FireclayItems.FIRED_TILES);
				output.accept(FireclayItems.FIRED_TILE_STAIRS);
				output.accept(FireclayItems.FIRED_TILE_SLAB);
				output.accept(FireclayItems.STORAGE_JAR);
				// Glazed pieces
				output.accept(FireclayItems.LUMEN_TILES);
				output.accept(FireclayItems.MEMORY_JAR);
				output.accept(FireclayItems.SENTRY_CHIME);
				output.accept(FireclayItems.VIGIL_URN);
				// The fireclay chain
				output.accept(FireclayItems.KILN_SHARD);
				output.accept(FireclayItems.GROG);
				output.accept(FireclayItems.FIRECLAY_BALL);
				output.accept(FireclayItems.FIRECLAY_BRICK);
				output.accept(FireclayItems.KILN_LINING);
				output.accept(FireclayItems.FIRECLAY_BRICKS);
				output.accept(FireclayItems.FIRECLAY_BRICK_STAIRS);
				output.accept(FireclayItems.FIRECLAY_BRICK_SLAB);
				output.accept(FireclayItems.FIRECLAY_BRICK_WALL);
				// High fire
				output.accept(FireclayItems.EMBER_TILE);
				output.accept(FireclayItems.RELIQUARY_URN);
				output.accept(FireclayItems.SENTINEL_EFFIGY);
			})
			.build());

	private FireclayCreativeTab() {
	}

	public static void init() {
	}
}
