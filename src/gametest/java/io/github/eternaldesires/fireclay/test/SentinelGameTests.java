package io.github.eternaldesires.fireclay.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.eternaldesires.fireclay.component.SentinelVitals;
import io.github.eternaldesires.fireclay.entity.TerracottaSentinel;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayEntities;
import io.github.eternaldesires.fireclay.registry.FireclayGameRules;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/** The Terracotta Sentinel (design §3.6): G18, G19, and the per-player cap. */
public class SentinelGameTests {
	/** Wider than the sentinel's 16-block guard radius, so it cannot pick up another test's zombie. */
	private static final int ISOLATION_PADDING = 18;
	private static final BlockPos GROUND = new BlockPos(3, 0, 3);

	/** Uses {@code player}'s main-hand item on the top face of {@code ground}, as a right click would. */
	private static InteractionResult useOnTop(GameTestHelper helper, Player player, BlockPos ground) {
		BlockPos abs = helper.absolutePos(ground);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0.0, 0.5, 0.0), Direction.UP, abs, false);
		return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	private static TerracottaSentinel onlySentinel(GameTestHelper helper) {
		List<TerracottaSentinel> sentinels = helper.getEntities(FireclayEntities.TERRACOTTA_SENTINEL);
		helper.assertValueEqual(sentinels.size(), 1, "sentinels in the test area");
		return sentinels.getFirst();
	}

	/**
	 * G18: the effigy awakens an invulnerable sentinel (60-tick countdown); once awake it targets a zombie 4 blocks
	 * away, and never a player.
	 */
	@GameTest(maxTicks = 400, padding = ISOLATION_PADDING)
	public void sentinelAwakensAndGuards(GameTestHelper helper) {
		TestSupport.floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FireclayItems.SENTINEL_EFFIGY));

		InteractionResult result = useOnTop(helper, player, GROUND);
		helper.assertTrue(result.consumesAction(), "Using the effigy should succeed, got " + result);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "A survival player's effigy is used up");
		TerracottaSentinel sentinel = onlySentinel(helper);
		helper.assertValueEqual(sentinel.getAwaken(), TerracottaSentinel.AWAKEN_TICKS, "awaken countdown");
		helper.assertValueEqual(sentinel.getOwnerId(), player.getUUID(), "owner");
		helper.assertTrue(sentinel.isInvulnerableTo(level, level.damageSources().generic()), "Awakening sentinels are invulnerable");
		helper.assertFalse(sentinel.canAttack(player), "Sentinels never attack players");

		Zombie[] zombie = new Zombie[1];
		helper.onEachTick(() -> {
			LivingEntity target = sentinel.getTarget();
			if (target instanceof Player) {
				helper.fail("The sentinel targeted a player");
			}
		});
		helper.startSequence()
				.thenExecuteAfter(TerracottaSentinel.AWAKEN_TICKS + 20, () -> {
					helper.assertFalse(sentinel.isAwakening(), "The sentinel should be awake by now");
					helper.assertFalse(sentinel.isInvulnerableTo(level, level.damageSources().generic()), "An awake sentinel can be hurt");
					zombie[0] = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, GROUND.above().south(4));
				})
				.thenWaitUntil(() -> helper.assertTrue(zombie[0].isDeadOrDying() || sentinel.getTarget() == zombie[0],
						"The sentinel has not engaged the zombie (target " + sentinel.getTarget() + ")"))
				.thenSucceed();
	}

	/** G19: the owner sneak-uses an awake sentinel at 34 HP and gets an effigy that remembers the 34 HP. */
	@GameTest(maxTicks = 140, padding = ISOLATION_PADDING)
	public void sentinelEffigyRoundtrip(GameTestHelper helper) {
		TestSupport.floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos post = helper.absolutePos(GROUND.above());
		TerracottaSentinel sentinel = FireclayEntities.TERRACOTTA_SENTINEL.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		helper.assertTrue(sentinel != null, "Could not create a sentinel");
		sentinel.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.0f, 0.0f);
		sentinel.awaken(player, post, Optional.empty(), null);
		level.addFreshEntity(sentinel);

		helper.startSequence()
				.thenExecuteAfter(TerracottaSentinel.AWAKEN_TICKS + 5, () -> {
					helper.assertFalse(sentinel.isAwakening(), "The sentinel should be awake");
					helper.assertValueEqual(sentinel.getMaxHealth(), SentinelVitals.BASE_MAX_HEALTH, "max health");
					sentinel.setHealth(34.0f);
					player.setShiftKeyDown(true);
					InteractionResult result = sentinel.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
					helper.assertTrue(result.consumesAction(), "Sneak + empty hand should pick the sentinel up, got " + result);
					helper.assertTrue(sentinel.isRemoved(), "The sentinel should be gone");

					ItemStack effigy = findEffigy(player.getInventory());
					helper.assertTrue(!effigy.isEmpty(), "The owner should receive an effigy");
					SentinelVitals vitals = effigy.get(FireclayComponents.SENTINEL_VITALS);
					helper.assertTrue(vitals != null && Math.abs(vitals.health() - 34.0f) < 0.01f,
							"The effigy should remember 34 HP, has " + vitals);
				})
				.thenSucceed();
	}

	/** With max_sentinels_per_player at 0 the effigy refuses and is kept. */
	@GameTest(maxTicks = 20, padding = ISOLATION_PADDING)
	public void sentinelLimitGamerule(GameTestHelper helper) {
		TestSupport.floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		GameRules rules = helper.getLevel().getGameRules();
		int old = rules.get(FireclayGameRules.MAX_SENTINELS_PER_PLAYER);
		rules.set(FireclayGameRules.MAX_SENTINELS_PER_PLAYER, 0, server);
		try {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FireclayItems.SENTINEL_EFFIGY));
			InteractionResult result = useOnTop(helper, player, GROUND);
			helper.assertFalse(result.consumesAction(), "The effigy should be refused at the cap, got " + result);
			helper.assertTrue(player.getMainHandItem().is(FireclayItems.SENTINEL_EFFIGY), "A refused effigy is kept");
			helper.assertTrue(helper.getEntities(FireclayEntities.TERRACOTTA_SENTINEL).isEmpty(), "No sentinel should appear");
		} finally {
			rules.set(FireclayGameRules.MAX_SENTINELS_PER_PLAYER, old, server);
		}
		helper.succeed();
	}

	private static ItemStack findEffigy(Inventory inventory) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(FireclayItems.SENTINEL_EFFIGY)) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}
}
