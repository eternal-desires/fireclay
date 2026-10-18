package io.github.eternaldesires.fireclay.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.kiln.FiringInput;
import io.github.eternaldesires.fireclay.kiln.FiringRecipe;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclayRecipes;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/**
 * G1: everything that only fails in the log at runtime (registrations, recipe JSON, tag JSON) actually loaded.
 * Recipes are checked by behaviour (does this grid craft that?) rather than by file name, so the test does not
 * depend on how the data generator names its recipe files.
 */
public class RegistryGameTests {
	private static final String[] BLOCKS = {
			"potters_wheel", "kiln", "bellows", "fired_tiles", "fired_tile_stairs", "fired_tile_slab", "lumen_tiles",
			"storage_jar", "memory_jar", "sentry_chime", "vigil_urn", "ember_tile", "reliquary_urn", "fireclay_bricks",
			"fireclay_brick_stairs", "fireclay_brick_slab", "fireclay_brick_wall"};
	private static final String[] ITEMS = {
			"unfired_tile", "unfired_bell", "unfired_jar", "unfired_urn", "unfired_effigy", "lapis_slip", "glowink_slip",
			"amethyst_slip", "blaze_slip", "echo_slip", "kiln_shard", "grog", "fireclay_ball", "fireclay_brick",
			"kiln_lining", "sentinel_effigy", "pyrometer"};

	/** G1a: every contract id is registered (contract §1, §2, §3, §6). */
	@GameTest(maxTicks = 20)
	public void registryContentsLoaded(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		requireIds(missing, BuiltInRegistries.BLOCK, BLOCKS);
		requireIds(missing, BuiltInRegistries.ITEM, BLOCKS);
		requireIds(missing, BuiltInRegistries.ITEM, ITEMS);
		requireIds(missing, BuiltInRegistries.BLOCK_ENTITY_TYPE, "kiln", "potters_wheel", "storage_jar", "memory_jar", "reliquary_urn");
		requireIds(missing, BuiltInRegistries.ENTITY_TYPE, "terracotta_sentinel");
		requireIds(missing, BuiltInRegistries.MENU, "kiln");
		requireIds(missing, BuiltInRegistries.RECIPE_TYPE, "firing");
		requireIds(missing, BuiltInRegistries.RECIPE_SERIALIZER, "firing");
		requireIds(missing, BuiltInRegistries.DATA_COMPONENT_TYPE, "masterwork", "stored_xp", "sentinel_vitals", "reliquary_charges");
		requireIds(missing, BuiltInRegistries.PARTICLE_TYPE, "kiln_ember");
		requireIds(missing, BuiltInRegistries.TRIGGER_TYPES, "potter_action");
		requireIds(missing, BuiltInRegistries.GAME_RULE, "kiln_crack_grace", "reliquary_urns", "max_sentinels_per_player");
		requireIds(missing, BuiltInRegistries.CREATIVE_MODE_TAB, "fireclay");
		requireIds(missing, BuiltInRegistries.SOUND_EVENT, "block.potters_wheel.throw", "block.bellows.pump", "block.kiln.roar",
				"block.kiln.overheat", "block.kiln.crack", "block.kiln.ping", "block.kiln.masterwork", "entity.terracotta_sentinel.awaken");
		helper.assertTrue(missing.isEmpty(), "Unregistered ids: " + missing);
		helper.succeed();
	}

	/** G1b: the item tags the kiln and the jars read from data JSON resolve (design §3.17). */
	@GameTest(maxTicks = 20)
	public void itemTagsLoaded(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		requireTag(wrong, Items.STICK, FireclayTags.Items.KILN_FUEL_KINDLING);
		requireTag(wrong, Items.OAK_LOG, FireclayTags.Items.KILN_FUEL_WOOD);
		requireTag(wrong, Items.OAK_PLANKS, FireclayTags.Items.KILN_FUEL_WOOD);
		requireTag(wrong, Items.COAL, FireclayTags.Items.KILN_FUEL_COAL);
		requireTag(wrong, Items.CHARCOAL, FireclayTags.Items.KILN_FUEL_COAL);
		requireTag(wrong, Items.COAL_BLOCK, FireclayTags.Items.KILN_FUEL_COAL_BLOCK);
		requireTag(wrong, Items.LAVA_BUCKET, FireclayTags.Items.KILN_FUEL_LAVA);
		requireTag(wrong, Items.BLAZE_ROD, FireclayTags.Items.KILN_FUEL_BLAZE);
		for (Item fuel : List.of(Items.STICK, Items.OAK_LOG, Items.COAL, Items.COAL_BLOCK, Items.LAVA_BUCKET, Items.BLAZE_ROD)) {
			requireTag(wrong, fuel, FireclayTags.Items.KILN_FUELS);
		}
		for (Item greenware : List.of(FireclayItems.UNFIRED_TILE, FireclayItems.UNFIRED_BELL, FireclayItems.UNFIRED_JAR,
				FireclayItems.UNFIRED_URN, FireclayItems.UNFIRED_EFFIGY)) {
			requireTag(wrong, greenware, FireclayTags.Items.GREENWARE);
		}
		for (Item glaze : List.of(FireclayItems.LAPIS_SLIP, FireclayItems.GLOWINK_SLIP, FireclayItems.AMETHYST_SLIP,
				FireclayItems.BLAZE_SLIP, FireclayItems.ECHO_SLIP)) {
			requireTag(wrong, glaze, FireclayTags.Items.GLAZES);
		}
		for (Item eligible : List.<Item>of(FireclayItems.MEMORY_JAR, FireclayItems.SENTRY_CHIME, FireclayItems.VIGIL_URN,
				FireclayItems.EMBER_TILE, FireclayItems.RELIQUARY_URN, FireclayItems.SENTINEL_EFFIGY)) {
			requireTag(wrong, eligible, FireclayTags.Items.MASTERWORK_ELIGIBLE);
		}
		requireTag(wrong, Items.SHULKER_BOX, FireclayTags.Items.STORAGE_JAR_BLACKLIST);
		requireTag(wrong, FireclayItems.STORAGE_JAR, FireclayTags.Items.STORAGE_JAR_BLACKLIST);
		if (new ItemStack(FireclayItems.STORAGE_JAR).is(FireclayTags.Items.MASTERWORK_ELIGIBLE)) {
			wrong.add("storage_jar must not be masterwork_eligible");
		}
		helper.assertTrue(wrong.isEmpty(), "Tag problems: " + wrong);
		helper.succeed();
	}

	/** G1c: all nine firing recipes (design §2.3) load with the right window, time and result. */
	@GameTest(maxTicks = 20)
	public void firingRecipesLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		firing(level, wrong, FireclayItems.UNFIRED_TILE, null, FireclayItems.FIRED_TILES, 4, 300, 600, 200);
		firing(level, wrong, FireclayItems.UNFIRED_JAR, null, FireclayItems.STORAGE_JAR, 1, 350, 600, 300);
		firing(level, wrong, FireclayItems.UNFIRED_TILE, FireclayItems.GLOWINK_SLIP, FireclayItems.LUMEN_TILES, 4, 480, 640, 300);
		firing(level, wrong, FireclayItems.UNFIRED_JAR, FireclayItems.LAPIS_SLIP, FireclayItems.MEMORY_JAR, 1, 540, 680, 400);
		firing(level, wrong, FireclayItems.UNFIRED_BELL, FireclayItems.AMETHYST_SLIP, FireclayItems.SENTRY_CHIME, 1, 600, 720, 400);
		firing(level, wrong, FireclayItems.UNFIRED_URN, FireclayItems.GLOWINK_SLIP, FireclayItems.VIGIL_URN, 1, 620, 740, 500);
		firing(level, wrong, FireclayItems.UNFIRED_TILE, FireclayItems.BLAZE_SLIP, FireclayItems.EMBER_TILE, 2, 760, 880, 400);
		firing(level, wrong, FireclayItems.UNFIRED_URN, FireclayItems.ECHO_SLIP, FireclayItems.RELIQUARY_URN, 1, 820, 900, 600);
		firing(level, wrong, FireclayItems.UNFIRED_EFFIGY, FireclayItems.BLAZE_SLIP, FireclayItems.SENTINEL_EFFIGY, 1, 860, 940, 800);

		// A wrong glaze, or a glaze on a bare recipe, matches nothing (design §2.3 matching rule).
		if (firingResult(level, FireclayItems.UNFIRED_TILE, FireclayItems.LAPIS_SLIP).isPresent()) {
			wrong.add("unfired_tile + lapis_slip should match no recipe");
		}
		if (firingResult(level, FireclayItems.UNFIRED_EFFIGY, null).isPresent()) {
			wrong.add("bare unfired_effigy should match no recipe");
		}
		helper.assertTrue(wrong.isEmpty(), "Firing recipe problems: " + wrong);
		helper.succeed();
	}

	/** G1d: the MUST crafting, smelting, blasting and stonecutting recipes (design §2.4) load. */
	@GameTest(maxTicks = 20)
	public void craftingRecipesLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		ItemStack e = ItemStack.EMPTY;
		ItemStack planks = new ItemStack(Items.OAK_PLANKS);
		ItemStack brick = new ItemStack(Items.BRICK);
		ItemStack fireclayBrick = new ItemStack(FireclayItems.FIRECLAY_BRICK);
		ItemStack tiles = new ItemStack(FireclayItems.FIRED_TILES);

		crafts(level, wrong, "potters_wheel", FireclayItems.POTTERS_WHEEL, 1, 3, 3,
				e, new ItemStack(Items.SMOOTH_STONE_SLAB), e,
				planks, new ItemStack(Items.STICK), planks,
				planks, planks, planks);
		crafts(level, wrong, "kiln", FireclayItems.KILN, 1, 3, 3,
				brick, new ItemStack(Items.IRON_BARS), brick,
				brick, new ItemStack(Items.FURNACE), brick,
				brick, new ItemStack(Items.IRON_BARS), brick);
		crafts(level, wrong, "bellows", FireclayItems.BELLOWS, 1, 3, 3,
				planks, planks, planks,
				new ItemStack(Items.LEATHER), new ItemStack(Items.LEATHER), new ItemStack(Items.LEATHER),
				planks, new ItemStack(Items.IRON_INGOT), planks);
		slip(level, wrong, "lapis_slip", FireclayItems.LAPIS_SLIP, 4, Items.LAPIS_LAZULI, 2);
		slip(level, wrong, "glowink_slip", FireclayItems.GLOWINK_SLIP, 4, Items.GLOW_INK_SAC, 2);
		slip(level, wrong, "amethyst_slip", FireclayItems.AMETHYST_SLIP, 4, Items.AMETHYST_SHARD, 2);
		slip(level, wrong, "blaze_slip", FireclayItems.BLAZE_SLIP, 4, Items.BLAZE_POWDER, 2);
		slip(level, wrong, "echo_slip", FireclayItems.ECHO_SLIP, 2, Items.ECHO_SHARD, 1);
		ItemStack shard = new ItemStack(FireclayItems.KILN_SHARD);
		crafts(level, wrong, "grog_from_shards", FireclayItems.GROG, 2, 2, 2, shard, shard, shard, shard);
		crafts(level, wrong, "grog_from_pot", FireclayItems.GROG, 2, 1, 1, new ItemStack(Items.DECORATED_POT));
		crafts(level, wrong, "fireclay_ball", FireclayItems.FIRECLAY_BALL, 3, 2, 2,
				new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL),
				new ItemStack(FireclayItems.GROG), new ItemStack(Items.SAND));
		crafts(level, wrong, "fireclay_ball_red_sand", FireclayItems.FIRECLAY_BALL, 3, 2, 2,
				new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL),
				new ItemStack(FireclayItems.GROG), new ItemStack(Items.RED_SAND));
		crafts(level, wrong, "kiln_lining", FireclayItems.KILN_LINING, 1, 3, 3,
				fireclayBrick, fireclayBrick, fireclayBrick,
				fireclayBrick, new ItemStack(Items.BLAZE_POWDER), fireclayBrick,
				fireclayBrick, fireclayBrick, fireclayBrick);
		crafts(level, wrong, "fired_tile_stairs", FireclayItems.FIRED_TILE_STAIRS, 4, 3, 3,
				tiles, e, e,
				tiles, tiles, e,
				tiles, tiles, tiles);
		crafts(level, wrong, "fired_tile_slab", FireclayItems.FIRED_TILE_SLAB, 6, 3, 1, tiles, tiles, tiles);
		squash(level, wrong, FireclayItems.UNFIRED_TILE, Items.CLAY_BALL, 1);
		squash(level, wrong, FireclayItems.UNFIRED_BELL, Items.CLAY_BALL, 3);
		squash(level, wrong, FireclayItems.UNFIRED_JAR, Items.CLAY_BALL, 4);
		squash(level, wrong, FireclayItems.UNFIRED_URN, Items.CLAY_BALL, 6);
		squash(level, wrong, FireclayItems.UNFIRED_EFFIGY, FireclayItems.FIRECLAY_BALL, 8);

		RecipeManager recipes = level.recipeAccess();
		ItemStack ball = new ItemStack(FireclayItems.FIRECLAY_BALL);
		Optional<ItemStack> smelted = recipes.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(ball), level)
				.map(holder -> holder.value().assemble(new SingleRecipeInput(ball)));
		expect(wrong, "smelting fireclay_ball", smelted, FireclayItems.FIRECLAY_BRICK, 1);
		Optional<ItemStack> blasted = recipes.getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(ball), level)
				.map(holder -> holder.value().assemble(new SingleRecipeInput(ball)));
		expect(wrong, "blasting fireclay_ball", blasted, FireclayItems.FIRECLAY_BRICK, 1);

		List<Item> cut = stonecutterResults(recipes, tiles);
		if (!cut.contains(FireclayItems.FIRED_TILE_STAIRS) || !cut.contains(FireclayItems.FIRED_TILE_SLAB)) {
			wrong.add("stonecutting fired_tiles gives " + cut + ", expected stairs and slab");
		}
		helper.assertTrue(wrong.isEmpty(), "Recipe problems: " + wrong);
		helper.succeed();
	}

	/** G1e (SHOULD content): pyrometer and fireclay-brick family recipes. Not required, so a gap only warns. */
	@GameTest(maxTicks = 20, required = false)
	public void shouldRecipesLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		ItemStack e = ItemStack.EMPTY;
		ItemStack copper = new ItemStack(Items.COPPER_INGOT);
		crafts(level, wrong, "pyrometer", FireclayItems.PYROMETER, 1, 3, 3,
				e, new ItemStack(Items.GLASS_PANE), e,
				copper, new ItemStack(Items.REDSTONE), copper,
				e, copper, e);
		ItemStack fireclayBrick = new ItemStack(FireclayItems.FIRECLAY_BRICK);
		crafts(level, wrong, "fireclay_bricks", FireclayItems.FIRECLAY_BRICKS, 4, 2, 2,
				fireclayBrick, fireclayBrick, fireclayBrick, fireclayBrick);
		List<Item> cut = stonecutterResults(level.recipeAccess(), new ItemStack(FireclayItems.FIRECLAY_BRICKS));
		for (Item expected : List.<Item>of(FireclayItems.FIRECLAY_BRICK_STAIRS, FireclayItems.FIRECLAY_BRICK_SLAB, FireclayItems.FIRECLAY_BRICK_WALL)) {
			if (!cut.contains(expected)) {
				wrong.add("stonecutting fireclay_bricks lacks " + expected);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "SHOULD recipe problems: " + wrong);
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	private static void requireIds(List<String> missing, Registry<?> registry, String... paths) {
		Arrays.stream(paths)
				.filter(path -> !registry.containsKey(Fireclay.id(path)))
				.forEach(path -> missing.add(registry.key().identifier() + " " + path));
	}

	private static void requireTag(List<String> wrong, Item item, TagKey<Item> tag) {
		if (!new ItemStack(item).is(tag)) {
			wrong.add(item + " not in #" + tag.location());
		}
	}

	private static Optional<RecipeHolder<FiringRecipe>> firingResult(ServerLevel level, Item greenware, Item glaze) {
		FiringInput input = new FiringInput(new ItemStack(greenware), glaze == null ? ItemStack.EMPTY : new ItemStack(glaze));
		return level.recipeAccess().getRecipeFor(FireclayRecipes.FIRING, input, level);
	}

	private static void firing(ServerLevel level, List<String> wrong, Item greenware, Item glaze, Item result, int count,
			int min, int max, int time) {
		String name = greenware + (glaze == null ? "" : " + " + glaze);
		Optional<RecipeHolder<FiringRecipe>> holder = firingResult(level, greenware, glaze);
		if (holder.isEmpty()) {
			wrong.add("no firing recipe for " + name);
			return;
		}
		FiringRecipe recipe = holder.get().value();
		ItemStack out = recipe.assemble(new FiringInput(new ItemStack(greenware), glaze == null ? ItemStack.EMPTY : new ItemStack(glaze)));
		if (!out.is(result) || out.getCount() != count) {
			wrong.add(name + " fires to " + TestSupport.describe(out) + ", expected " + count + "x " + result);
		}
		if (recipe.minHeat() != min || recipe.maxHeat() != max || recipe.time() != time) {
			wrong.add(name + " window " + recipe.minHeat() + "-" + recipe.maxHeat() + "/" + recipe.time()
					+ ", expected " + min + "-" + max + "/" + time);
		}
	}

	private static void crafts(ServerLevel level, List<String> wrong, String name, Item result, int count, int width, int height,
			ItemStack... grid) {
		CraftingInput input = CraftingInput.of(width, height, List.of(grid));
		Optional<ItemStack> out = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input));
		expect(wrong, name, out, result, count);
	}

	/** Slips: clay ball, water bucket and the mineral, shapeless in a 2×2 grid. */
	private static void slip(ServerLevel level, List<String> wrong, String name, Item result, int count, Item mineral, int minerals) {
		ItemStack second = minerals > 1 ? new ItemStack(mineral) : ItemStack.EMPTY;
		crafts(level, wrong, name, result, count, 2, 2,
				new ItemStack(Items.CLAY_BALL), new ItemStack(Items.WATER_BUCKET), new ItemStack(mineral), second);
	}

	private static void squash(ServerLevel level, List<String> wrong, Item greenware, Item clay, int count) {
		crafts(level, wrong, "squash " + greenware, clay, count, 1, 1, new ItemStack(greenware));
	}

	private static void expect(List<String> wrong, String name, Optional<ItemStack> out, Item result, int count) {
		if (out.isEmpty()) {
			wrong.add(name + ": no recipe");
		} else if (!out.get().is(result) || out.get().getCount() != count) {
			wrong.add(name + ": got " + TestSupport.describe(out.get()) + ", expected " + count + "x " + result);
		}
	}

	private static List<Item> stonecutterResults(RecipeManager recipes, ItemStack input) {
		SingleRecipeInput single = new SingleRecipeInput(input);
		List<Item> results = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes.getRecipes()) {
			if (holder.value() instanceof StonecutterRecipe recipe && recipe.input().test(input)) {
				results.add(recipe.assemble(single).getItem());
			}
		}
		return results;
	}
}
