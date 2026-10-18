package io.github.eternaldesires.fireclay;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eternaldesires.fireclay.block.CeramicsFeature;
import io.github.eternaldesires.fireclay.block.WorkshopFeature;
import io.github.eternaldesires.fireclay.command.SystemsFeature;
import io.github.eternaldesires.fireclay.entity.SentinelFeature;
import io.github.eternaldesires.fireclay.kiln.KilnFeature;
import io.github.eternaldesires.fireclay.registry.FireclayAttachments;
import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayBlocks;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayCreativeTab;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayEntities;
import io.github.eternaldesires.fireclay.registry.FireclayGameRules;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclayMenus;
import io.github.eternaldesires.fireclay.registry.FireclayNetworking;
import io.github.eternaldesires.fireclay.registry.FireclayParticles;
import io.github.eternaldesires.fireclay.registry.FireclayRecipes;
import io.github.eternaldesires.fireclay.registry.FireclaySounds;

public class Fireclay implements ModInitializer {
	public static final String MOD_ID = "fireclay";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Registries first. Components come before items so item properties may reference them.
		FireclayComponents.init();
		FireclaySounds.init();
		FireclayParticles.init();
		FireclayBlocks.init();
		FireclayItems.init();
		FireclayBlockEntities.init();
		FireclayMenus.init();
		FireclayRecipes.init();
		FireclayEntities.init();
		FireclayAttachments.init();
		FireclayCriteria.init();
		FireclayGameRules.init();
		FireclayNetworking.init();
		FireclayCreativeTab.init();

		// Feature hooks: each feature registers its own events here.
		KilnFeature.register();
		WorkshopFeature.register();
		CeramicsFeature.register();
		SentinelFeature.register();
		SystemsFeature.register();

		LOGGER.info("Clay remembers the fire.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
