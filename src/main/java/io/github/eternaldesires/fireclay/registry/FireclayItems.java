package io.github.eternaldesires.fireclay.registry;

import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.item.GreenwareItem;
import io.github.eternaldesires.fireclay.item.MasterworkBlockItem;
import io.github.eternaldesires.fireclay.item.SentinelEffigyItem;

/** Every item (contract §2) plus one block item per block (same id, {@code block.fireclay.<id>} name). */
public final class FireclayItems {
	// Block items
	public static final BlockItem POTTERS_WHEEL = block(FireclayBlocks.POTTERS_WHEEL);
	public static final BlockItem KILN = block(FireclayBlocks.KILN);
	public static final BlockItem BELLOWS = block(FireclayBlocks.BELLOWS);
	public static final BlockItem FIRED_TILES = block(FireclayBlocks.FIRED_TILES);
	public static final BlockItem FIRED_TILE_STAIRS = block(FireclayBlocks.FIRED_TILE_STAIRS);
	public static final BlockItem FIRED_TILE_SLAB = block(FireclayBlocks.FIRED_TILE_SLAB);
	public static final BlockItem LUMEN_TILES = block(FireclayBlocks.LUMEN_TILES);
	/** Stacks to 1: it carries its contents as a component. */
	public static final BlockItem STORAGE_JAR = block(FireclayBlocks.STORAGE_JAR, BlockItem::new, new Item.Properties().stacksTo(1));
	/** Stacks to 1: it carries stored XP as a component. */
	public static final MasterworkBlockItem MEMORY_JAR = block(FireclayBlocks.MEMORY_JAR, MasterworkBlockItem::new, new Item.Properties().stacksTo(1));
	public static final MasterworkBlockItem SENTRY_CHIME = block(FireclayBlocks.SENTRY_CHIME, MasterworkBlockItem::new, new Item.Properties());
	public static final MasterworkBlockItem VIGIL_URN = block(FireclayBlocks.VIGIL_URN, MasterworkBlockItem::new, new Item.Properties());
	public static final MasterworkBlockItem EMBER_TILE = block(FireclayBlocks.EMBER_TILE, MasterworkBlockItem::new, new Item.Properties());
	/** Stacks to 1: it carries its remaining charges as a component. */
	public static final MasterworkBlockItem RELIQUARY_URN = block(FireclayBlocks.RELIQUARY_URN, MasterworkBlockItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final BlockItem FIRECLAY_BRICKS = block(FireclayBlocks.FIRECLAY_BRICKS);
	public static final BlockItem FIRECLAY_BRICK_STAIRS = block(FireclayBlocks.FIRECLAY_BRICK_STAIRS);
	public static final BlockItem FIRECLAY_BRICK_SLAB = block(FireclayBlocks.FIRECLAY_BRICK_SLAB);
	public static final BlockItem FIRECLAY_BRICK_WALL = block(FireclayBlocks.FIRECLAY_BRICK_WALL);

	// Greenware (tag fireclay:greenware)
	public static final GreenwareItem UNFIRED_TILE = register("unfired_tile", GreenwareItem::new, new Item.Properties());
	public static final GreenwareItem UNFIRED_BELL = register("unfired_bell", GreenwareItem::new, new Item.Properties());
	public static final GreenwareItem UNFIRED_JAR = register("unfired_jar", GreenwareItem::new, new Item.Properties());
	public static final GreenwareItem UNFIRED_URN = register("unfired_urn", GreenwareItem::new, new Item.Properties());
	public static final GreenwareItem UNFIRED_EFFIGY = register("unfired_effigy", GreenwareItem::new, new Item.Properties());

	// Slips (tag fireclay:glazes)
	public static final Item LAPIS_SLIP = simple("lapis_slip");
	public static final Item GLOWINK_SLIP = simple("glowink_slip");
	public static final Item AMETHYST_SLIP = simple("amethyst_slip");
	public static final Item BLAZE_SLIP = simple("blaze_slip");
	public static final Item ECHO_SLIP = simple("echo_slip");

	// Materials
	public static final Item KILN_SHARD = simple("kiln_shard");
	public static final Item GROG = simple("grog");
	public static final Item FIRECLAY_BALL = simple("fireclay_ball");
	public static final Item FIRECLAY_BRICK = simple("fireclay_brick");
	public static final Item KILN_LINING = simple("kiln_lining");

	// Tools and pieces
	public static final SentinelEffigyItem SENTINEL_EFFIGY = register("sentinel_effigy", SentinelEffigyItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	/** Plain item: the server-tick sender in the systems feature checks for it in either hand. */
	public static final Item PYROMETER = register("pyrometer", Item::new, new Item.Properties().stacksTo(1));

	private FireclayItems() {
	}

	private static Item simple(String path) {
		return register(path, Item::new, new Item.Properties());
	}

	private static <T extends Item> T register(String path, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Fireclay.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static BlockItem block(Block block) {
		return block(block, BlockItem::new, new Item.Properties());
	}

	/** Registers a block item under the block's own id with the {@code block.} description prefix. */
	private static <T extends BlockItem> T block(Block block, BiFunction<Block, Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(block, properties.useBlockDescriptionPrefix().setId(key)));
	}

	public static void init() {
	}
}
