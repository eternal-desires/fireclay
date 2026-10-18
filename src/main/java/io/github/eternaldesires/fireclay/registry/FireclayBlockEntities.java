package io.github.eternaldesires.fireclay.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.block.MemoryJarBlockEntity;
import io.github.eternaldesires.fireclay.block.PottersWheelBlockEntity;
import io.github.eternaldesires.fireclay.block.ReliquaryUrnBlockEntity;
import io.github.eternaldesires.fireclay.block.StorageJarBlockEntity;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;

/** Block entity types (contract §6). Fabric's builder is used because the vanilla constructor is not widened for mods. */
public final class FireclayBlockEntities {
	public static final BlockEntityType<KilnBlockEntity> KILN =
			register("kiln", KilnBlockEntity::new, FireclayBlocks.KILN);
	public static final BlockEntityType<PottersWheelBlockEntity> POTTERS_WHEEL =
			register("potters_wheel", PottersWheelBlockEntity::new, FireclayBlocks.POTTERS_WHEEL);
	public static final BlockEntityType<StorageJarBlockEntity> STORAGE_JAR =
			register("storage_jar", StorageJarBlockEntity::new, FireclayBlocks.STORAGE_JAR);
	public static final BlockEntityType<MemoryJarBlockEntity> MEMORY_JAR =
			register("memory_jar", MemoryJarBlockEntity::new, FireclayBlocks.MEMORY_JAR);
	public static final BlockEntityType<ReliquaryUrnBlockEntity> RELIQUARY_URN =
			register("reliquary_urn", ReliquaryUrnBlockEntity::new, FireclayBlocks.RELIQUARY_URN);

	private FireclayBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String path, FabricBlockEntityTypeBuilder.Factory<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Fireclay.id(path),
				FabricBlockEntityTypeBuilder.<T>create(factory, blocks).build());
	}

	public static void init() {
	}
}
