package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.kiln.FiringRecipe;

/**
 * The {@code fireclay:firing} recipe type and serializer. In 26.3 a serializer is a plain record of a MapCodec
 * and a StreamCodec, so there is no serializer class. Recipes are not synced to clients; the kiln screen gets
 * the window through ContainerData.
 */
public final class FireclayRecipes {
	public static final RecipeType<FiringRecipe> FIRING = Registry.register(
			BuiltInRegistries.RECIPE_TYPE, Fireclay.id("firing"), new RecipeType<FiringRecipe>() {
				@Override
				public String toString() {
					return "fireclay:firing";
				}
			});

	public static final RecipeSerializer<FiringRecipe> FIRING_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER, Fireclay.id("firing"),
			new RecipeSerializer<>(FiringRecipe.CODEC, FiringRecipe.STREAM_CODEC));

	private FireclayRecipes() {
	}

	public static void init() {
	}
}
