package io.github.eternaldesires.fireclay.test;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.eternaldesires.fireclay.block.MemoryJarBlockEntity;
import io.github.eternaldesires.fireclay.block.ReliquaryData;
import io.github.eternaldesires.fireclay.block.ReliquaryEvents;
import io.github.eternaldesires.fireclay.block.ReliquaryUrnBlock;
import io.github.eternaldesires.fireclay.block.SentryChimeBlock;
import io.github.eternaldesires.fireclay.block.StorageJarBlockEntity;
import io.github.eternaldesires.fireclay.block.VigilUrnBlock;
import io.github.eternaldesires.fireclay.component.StoredXp;
import io.github.eternaldesires.fireclay.registry.FireclayBlocks;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/** Fired ceramics (design §3.2, §3.11): G13–G16, G21, G22. */
public class CeramicsGameTests {
	/** Empty layers around entity-scanning tests, wider than the chime's 12-block range. */
	static final int ISOLATION_PADDING = 14;

	/** G13: depositing at level 10 (empty bar) moves the 25 points of level 9; withdrawing gives them back. */
	@GameTest(maxTicks = 20)
	public void memoryJarDepositWithdraw(GameTestHelper helper) {
		BlockPos jarPos = new BlockPos(2, 1, 2);
		helper.setBlock(jarPos, FireclayBlocks.MEMORY_JAR);
		MemoryJarBlockEntity jar = helper.getBlockEntity(jarPos, MemoryJarBlockEntity.class);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.experienceLevel = 10;
		player.experienceProgress = 0.0f;

		helper.useBlock(jarPos, player);
		helper.assertValueEqual(jar.getXp(), 25, "jar xp after deposit");
		helper.assertValueEqual(player.experienceLevel, 9, "player level after deposit");

		player.setShiftKeyDown(true);
		helper.useBlock(jarPos, player);
		helper.assertValueEqual(player.experienceLevel, 10, "player level after withdraw");
		helper.assertValueEqual(jar.getXp(), 0, "jar xp after withdraw");
		helper.succeed();
	}

	/** G14: broken jars drop themselves with their contents as components, and placing them restores the contents. */
	@GameTest(maxTicks = 20)
	public void jarsKeepContentsWhenBroken(GameTestHelper helper) {
		TestSupport.floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storagePos = new BlockPos(1, 1, 1);
		BlockPos memoryPos = new BlockPos(6, 1, 6);
		helper.setBlock(storagePos, FireclayBlocks.STORAGE_JAR);
		helper.setBlock(memoryPos, FireclayBlocks.MEMORY_JAR);
		helper.getBlockEntity(storagePos, StorageJarBlockEntity.class).setItem(4, new ItemStack(Items.DIAMOND, 5));
		helper.getBlockEntity(memoryPos, MemoryJarBlockEntity.class).setXp(300);

		level.destroyBlock(helper.absolutePos(storagePos), true);
		level.destroyBlock(helper.absolutePos(memoryPos), true);
		helper.assertItemEntityNotPresent(Items.DIAMOND, storagePos, 2.0);

		ItemStack storageDrop = TestSupport.findDrop(helper, FireclayItems.STORAGE_JAR, storagePos, 2.0);
		ItemContainerContents contents = storageDrop.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		int diamonds = contents.nonEmptyItemCopyStream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
		helper.assertValueEqual(diamonds, 5, "diamonds in the dropped storage jar's container component");

		ItemStack memoryDrop = TestSupport.findDrop(helper, FireclayItems.MEMORY_JAR, memoryPos, 2.0);
		helper.assertValueEqual(memoryDrop.getOrDefault(FireclayComponents.STORED_XP, StoredXp.EMPTY).points(), 300,
				"stored_xp on the dropped memory jar");

		// Re-place both through setPlacedBy, as BlockItem placement does.
		BlockPos storageAgain = new BlockPos(3, 1, 1);
		BlockPos memoryAgain = new BlockPos(3, 1, 6);
		helper.setBlock(storageAgain, FireclayBlocks.STORAGE_JAR);
		helper.setBlock(memoryAgain, FireclayBlocks.MEMORY_JAR);
		FireclayBlocks.STORAGE_JAR.setPlacedBy(level, helper.absolutePos(storageAgain), helper.getBlockState(storageAgain), null, storageDrop);
		FireclayBlocks.MEMORY_JAR.setPlacedBy(level, helper.absolutePos(memoryAgain), helper.getBlockState(memoryAgain), null, memoryDrop);

		StorageJarBlockEntity storage = helper.getBlockEntity(storageAgain, StorageJarBlockEntity.class);
		int restored = 0;
		for (int slot = 0; slot < storage.getContainerSize(); slot++) {
			if (storage.getItem(slot).is(Items.DIAMOND)) {
				restored += storage.getItem(slot).getCount();
			}
		}
		helper.assertValueEqual(restored, 5, "diamonds in the re-placed storage jar");
		helper.assertValueEqual(helper.getBlockEntity(memoryAgain, MemoryJarBlockEntity.class).getXp(), 300, "xp in the re-placed memory jar");
		helper.succeed();
	}

	/**
	 * G15: the chime's power falls off with the nearest hostile's distance and marks it GLOWING. The wide padding
	 * keeps other tests' mobs outside the chime's 12-block range.
	 */
	@GameTest(maxTicks = 80, padding = ISOLATION_PADDING)
	public void sentryChimeDetectsAndScales(GameTestHelper helper) {
		TestSupport.floor(helper);
		BlockPos chimePos = new BlockPos(0, 1, 0);
		helper.setBlock(chimePos, FireclayBlocks.SENTRY_CHIME);
		// Support for the far position, so the zombie has somewhere to stand.
		helper.setBlock(7, 3, 7, Blocks.STONE);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 1, 0));
		zombie.setNoAi(true);
		int wait = SentryChimeBlock.SCAN_INTERVAL + 2;

		helper.startSequence()
				.thenExecuteAfter(wait, () -> {
					int power = helper.getBlockState(chimePos).getValue(SentryChimeBlock.POWER);
					helper.assertTrue(power >= 12, "Zombie at ~3 blocks: power " + power + ", expected >= 12");
					helper.assertTrue(zombie.hasEffect(MobEffects.GLOWING), "A detected hostile should glow");
					Vec3 far = helper.absoluteVec(new Vec3(7.5, 4.0, 7.5));
					zombie.teleportTo(far.x, far.y, far.z);
				})
				.thenExecuteAfter(wait, () -> {
					int power = helper.getBlockState(chimePos).getValue(SentryChimeBlock.POWER);
					helper.assertTrue(power >= 1 && power <= 5, "Zombie at ~10 blocks: power " + power + ", expected 1-5");
					zombie.discard();
				})
				.thenExecuteAfter(wait, () -> helper.assertBlockProperty(chimePos, SentryChimeBlock.POWER, 0))
				.thenSucceed();
	}

	/** G16: the vigil urn pushes a zombie out past 3 blocks, calms a swelling creeper, and leaves vigil_immune mobs alone. */
	@GameTest(maxTicks = 100, padding = ISOLATION_PADDING)
	public void vigilUrnPushesAndDefuses(GameTestHelper helper) {
		TestSupport.floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos urnPos = new BlockPos(3, 1, 3);
		helper.setBlock(urnPos, FireclayBlocks.VIGIL_URN);
		Vec3 urnCenter = helper.absoluteVec(new Vec3(3.5, 1.5, 3.5));
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1, 3));
		Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new BlockPos(1, 1, 3));
		creeper.setSwellDir(1);

		// Tag check as a unit: a warden is never repelled (and so never pushed), a zombie is.
		Warden warden = EntityTypes.WARDEN.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(warden != null && warden.is(FireclayTags.EntityTypes.VIGIL_IMMUNE), "Wardens should be #fireclay:vigil_immune");
		helper.assertFalse(VigilUrnBlock.isRepelled(warden), "A warden must not be repelled");
		helper.assertTrue(VigilUrnBlock.isRepelled(zombie), "A zombie must be repelled");

		helper.succeedWhen(() -> {
			double dx = zombie.getX() - urnCenter.x;
			double dz = zombie.getZ() - urnCenter.z;
			double distance = Math.sqrt(dx * dx + dz * dz);
			helper.assertTrue(distance > 3.0, "Zombie still " + distance + " blocks from the urn");
			helper.assertValueEqual(creeper.getSwellDir(), -1, "creeper swell direction");
		});
	}

	/** G21 (SHOULD): eight raw iron resting on an ember tile bake into eight iron ingots. */
	@GameTest(maxTicks = 300)
	public void emberTileBakesRawIron(GameTestHelper helper) {
		BlockPos tile = new BlockPos(3, 1, 3);
		helper.setBlock(tile, FireclayBlocks.EMBER_TILE);
		Vec3 top = helper.absoluteVec(new Vec3(3.5, 2.05, 3.5));
		ItemEntity iron = new ItemEntity(helper.getLevel(), top.x, top.y, top.z, new ItemStack(Items.RAW_IRON, 8));
		iron.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(iron);

		helper.succeedWhen(() -> {
			helper.assertValueEqual(TestSupport.countItemEntities(helper, Items.IRON_INGOT), 8, "iron ingots baked");
			helper.assertValueEqual(TestSupport.countItemEntities(helper, Items.RAW_IRON), 0, "raw iron left");
		});
	}

	/**
	 * G22 (SHOULD): a bound urn catches a dying player's inventory (nothing drops), and using it hands everything
	 * back. With a totem in hand the urn is skipped.
	 */
	@GameTest(maxTicks = 20)
	public void reliquaryCapturesInventory(GameTestHelper helper) {
		TestSupport.floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos urnPos = new BlockPos(3, 1, 3);
		helper.setBlock(urnPos, FireclayBlocks.RELIQUARY_URN);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerPlayer totemPlayer = helper.makeMockServerPlayerInLevel();
		try {
			player.snapTo(helper.absoluteVec(new Vec3(3.5, 1.0, 5.5)));
			helper.useBlock(urnPos, player);
			ReliquaryData data = ReliquaryData.get(level.getServer());
			helper.assertTrue(data.get(player.getUUID()).isPresent(), "Using the urn should bind it");

			Inventory inventory = player.getInventory();
			inventory.setItem(0, new ItemStack(Items.DIRT, 16));
			inventory.setItem(1, new ItemStack(Items.DIAMOND, 3));
			inventory.setItem(2, new ItemStack(Items.STICK, 5));
			player.setGameMode(GameType.SURVIVAL);
			DamageSource kill = player.damageSources().genericKill();
			player.hurtServer(level, kill, Float.MAX_VALUE);
			if (!player.isDeadOrDying()) {
				// Mock players can be shielded from damage; fire the death hook our listener is registered on directly.
				ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player, kill, Float.MAX_VALUE);
			}

			helper.assertTrue(helper.getEntities(EntityTypes.ITEM).isEmpty(), "Nothing should drop when the urn catches it");
			Optional<ReliquaryData.Binding> binding = data.get(player.getUUID());
			helper.assertTrue(binding.isPresent() && binding.get().held().size() == 3,
					"The urn should hold 3 stacks, holds " + binding.map(b -> b.held().size()).orElse(-1));
			helper.assertBlockProperty(urnPos, ReliquaryUrnBlock.FILLED, true);

			helper.useBlock(urnPos, player);
			helper.assertTrue(inventory.getItem(0).is(Items.DIRT) && inventory.getItem(0).getCount() == 16, "dirt restored to slot 0");
			helper.assertTrue(inventory.getItem(1).is(Items.DIAMOND) && inventory.getItem(1).getCount() == 3, "diamonds restored to slot 1");
			helper.assertTrue(inventory.getItem(2).is(Items.STICK) && inventory.getItem(2).getCount() == 5, "sticks restored to slot 2");
			// One charge: the urn crumbles after giving everything back.
			helper.assertBlockNotPresent(FireclayBlocks.RELIQUARY_URN, urnPos);
			helper.assertTrue(data.get(player.getUUID()).isEmpty(), "The spent urn should unbind");

			// A totem in either hand means the urn stays out of it.
			BlockPos secondUrn = new BlockPos(5, 1, 3);
			helper.setBlock(secondUrn, FireclayBlocks.RELIQUARY_URN);
			helper.useBlock(secondUrn, totemPlayer);
			totemPlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			totemPlayer.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
			helper.assertFalse(ReliquaryEvents.capture(totemPlayer), "A totem holder must not be captured");
			helper.assertTrue(totemPlayer.getInventory().getItem(0).is(Items.DIAMOND), "The totem holder keeps the inventory");
			helper.assertTrue(data.get(totemPlayer.getUUID()).map(b -> b.held().isEmpty()).orElse(false), "Nothing captured");
		} finally {
			ReliquaryData data = ReliquaryData.get(level.getServer());
			data.unbind(player.getUUID());
			data.unbind(totemPlayer.getUUID());
			level.getServer().getPlayerList().remove(player);
			level.getServer().getPlayerList().remove(totemPlayer);
		}
		helper.succeed();
	}
}
