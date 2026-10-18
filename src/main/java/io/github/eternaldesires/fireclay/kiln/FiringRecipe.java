package io.github.eternaldesires.fireclay.kiln;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import io.github.eternaldesires.fireclay.registry.FireclayRecipes;

/**
 * A {@code fireclay:firing} recipe (design §2.3):
 * {@code {"type": "fireclay:firing", "greenware": ..., "glaze": ... (optional), "min_heat": 540, "max_heat": 680,
 * "time": 400, "result": {"id": "fireclay:memory_jar"}}}.
 */
public class FiringRecipe implements Recipe<FiringInput> {
	public static final MapCodec<FiringRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Ingredient.CODEC.fieldOf("greenware").forGetter(FiringRecipe::greenware),
			Ingredient.CODEC.optionalFieldOf("glaze").forGetter(FiringRecipe::glaze),
			Codec.INT.fieldOf("min_heat").forGetter(FiringRecipe::minHeat),
			Codec.INT.fieldOf("max_heat").forGetter(FiringRecipe::maxHeat),
			ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(FiringRecipe::time),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(FiringRecipe::result)
	).apply(instance, FiringRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, FiringRecipe> STREAM_CODEC = StreamCodec.composite(
			Ingredient.CONTENTS_STREAM_CODEC, FiringRecipe::greenware,
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, FiringRecipe::glaze,
			ByteBufCodecs.VAR_INT, FiringRecipe::minHeat,
			ByteBufCodecs.VAR_INT, FiringRecipe::maxHeat,
			ByteBufCodecs.VAR_INT, FiringRecipe::time,
			ItemStackTemplate.STREAM_CODEC, FiringRecipe::result,
			FiringRecipe::new);

	private final Ingredient greenware;
	private final Optional<Ingredient> glaze;
	private final int minHeat;
	private final int maxHeat;
	private final int time;
	private final ItemStackTemplate result;

	public FiringRecipe(Ingredient greenware, Optional<Ingredient> glaze, int minHeat, int maxHeat, int time, ItemStackTemplate result) {
		this.greenware = greenware;
		this.glaze = glaze;
		this.minHeat = minHeat;
		this.maxHeat = maxHeat;
		this.time = time;
		this.result = result;
	}

	public Ingredient greenware() {
		return greenware;
	}

	public Optional<Ingredient> glaze() {
		return glaze;
	}

	public int minHeat() {
		return minHeat;
	}

	public int maxHeat() {
		return maxHeat;
	}

	/** Ticks of in-window heat needed to finish. */
	public int time() {
		return time;
	}

	public ItemStackTemplate result() {
		return result;
	}

	/** Sweet spot: the middle 40% of the window. */
	public double sweetMin() {
		return minHeat + 0.3 * (maxHeat - minHeat);
	}

	public double sweetMax() {
		return maxHeat - 0.3 * (maxHeat - minHeat);
	}

	/** A glazed recipe needs both ingredients; a bare recipe matches only while the glaze slot is empty. */
	@Override
	public boolean matches(FiringInput input, Level level) {
		if (!greenware.test(input.greenware())) {
			return false;
		}
		return glaze.map(ingredient -> ingredient.test(input.glaze())).orElseGet(() -> input.glaze().isEmpty());
	}

	@Override
	public ItemStack assemble(FiringInput input) {
		return result.create();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeSerializer<FiringRecipe> getSerializer() {
		return FireclayRecipes.FIRING_SERIALIZER;
	}

	@Override
	public RecipeType<FiringRecipe> getType() {
		return FireclayRecipes.FIRING;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public @Nullable RecipeBookCategory recipeBookCategory() {
		return null;
	}
}
