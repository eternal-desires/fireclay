package io.github.eternaldesires.fireclay.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.block.BellowsBlock;
import io.github.eternaldesires.fireclay.block.EmberTileBlock;
import io.github.eternaldesires.fireclay.block.MemoryJarBlock;
import io.github.eternaldesires.fireclay.block.PottersWheelBlock;
import io.github.eternaldesires.fireclay.block.ReliquaryUrnBlock;
import io.github.eternaldesires.fireclay.block.SentryChimeBlock;
import io.github.eternaldesires.fireclay.block.StorageJarBlock;
import io.github.eternaldesires.fireclay.block.VigilUrnBlock;
import io.github.eternaldesires.fireclay.kiln.KilnBlock;

/** Every block (contract §1). Block items are registered in {@link FireclayItems}. */
public final class FireclayBlocks {
	// Workshop
	public static final PottersWheelBlock POTTERS_WHEEL = register("potters_wheel", PottersWheelBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
	public static final KilnBlock KILN = register("kiln", KilnBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.5f).sound(SoundType.STONE)
					.requiresCorrectToolForDrops().lightLevel(KilnBlock::lightLevel));
	public static final BellowsBlock BELLOWS = register("bellows", BellowsBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());

	// Fired tiles
	public static final Block FIRED_TILES = register("fired_tiles", Block::new, tiles(MapColor.TERRACOTTA_ORANGE));
	public static final StairBlock FIRED_TILE_STAIRS = register("fired_tile_stairs",
			properties -> new StairBlock(FIRED_TILES.defaultBlockState(), properties), tiles(MapColor.TERRACOTTA_ORANGE));
	public static final SlabBlock FIRED_TILE_SLAB = register("fired_tile_slab", SlabBlock::new, tiles(MapColor.TERRACOTTA_ORANGE));
	public static final Block LUMEN_TILES = register("lumen_tiles", Block::new,
			tiles(MapColor.COLOR_CYAN).lightLevel(state -> 15));

	// Ceramics
	public static final StorageJarBlock STORAGE_JAR = register("storage_jar", StorageJarBlock::new,
			ceramic(MapColor.TERRACOTTA_ORANGE, 1.0f));
	public static final MemoryJarBlock MEMORY_JAR = register("memory_jar", MemoryJarBlock::new,
			ceramic(MapColor.LAPIS, 1.0f).lightLevel(MemoryJarBlock::lightLevel));
	public static final SentryChimeBlock SENTRY_CHIME = register("sentry_chime", SentryChimeBlock::new,
			ceramic(MapColor.COLOR_PURPLE, 1.0f).sound(SoundType.AMETHYST));
	public static final VigilUrnBlock VIGIL_URN = register("vigil_urn", VigilUrnBlock::new,
			ceramic(MapColor.COLOR_CYAN, 1.0f).lightLevel(state -> 15));
	public static final EmberTileBlock EMBER_TILE = register("ember_tile", EmberTileBlock::new,
			tiles(MapColor.COLOR_ORANGE).lightLevel(state -> 7));
	public static final ReliquaryUrnBlock RELIQUARY_URN = register("reliquary_urn", ReliquaryUrnBlock::new,
			ceramic(MapColor.TERRACOTTA_CYAN, 2.0f));

	// Fireclay bricks
	public static final Block FIRECLAY_BRICKS = register("fireclay_bricks", Block::new, bricks());
	public static final StairBlock FIRECLAY_BRICK_STAIRS = register("fireclay_brick_stairs",
			properties -> new StairBlock(FIRECLAY_BRICKS.defaultBlockState(), properties), bricks());
	public static final SlabBlock FIRECLAY_BRICK_SLAB = register("fireclay_brick_slab", SlabBlock::new, bricks());
	public static final WallBlock FIRECLAY_BRICK_WALL = register("fireclay_brick_wall", WallBlock::new, bricks().forceSolidOn());

	private FireclayBlocks() {
	}

	/** Glazed and bare tiles: pickaxe, like terracotta. */
	private static BlockBehaviour.Properties tiles(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(1.5f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops();
	}

	/** Thrown-and-fired pieces: quick to pick up, never lost when broken by hand. */
	private static BlockBehaviour.Properties ceramic(MapColor color, float strength) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(strength).sound(SoundType.DECORATED_POT).noOcclusion();
	}

	private static BlockBehaviour.Properties bricks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops();
	}

	private static <T extends Block> T register(String path, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Fireclay.id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
