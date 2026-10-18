package io.github.eternaldesires.fireclay.registry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;

import io.github.eternaldesires.fireclay.Fireclay;

/** Game rules (design §3.15). Lang keys: {@code gamerule.fireclay.<id>} and {@code gamerule.fireclay.<id>.description}. */
public final class FireclayGameRules {
	/** Ticks a firing may stay above max_heat before it cracks. */
	public static final GameRule<Integer> KILN_CRACK_GRACE = GameRuleBuilder.forInteger(40)
			.range(1, 1200)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(Fireclay.id("kiln_crack_grace"));
	/** Whether bound Reliquary Urns catch a dying player's inventory. */
	public static final GameRule<Boolean> RELIQUARY_URNS = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(Fireclay.id("reliquary_urns"));
	/** How many Sentinels one player may have awake at once. */
	public static final GameRule<Integer> MAX_SENTINELS_PER_PLAYER = GameRuleBuilder.forInteger(4)
			.minValue(0)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(Fireclay.id("max_sentinels_per_player"));

	private FireclayGameRules() {
	}

	public static int crackGrace(ServerLevel level) {
		return level.getGameRules().get(KILN_CRACK_GRACE);
	}

	public static boolean reliquaryUrns(ServerLevel level) {
		return level.getGameRules().get(RELIQUARY_URNS);
	}

	public static int maxSentinelsPerPlayer(ServerLevel level) {
		return level.getGameRules().get(MAX_SENTINELS_PER_PLAYER);
	}

	public static void init() {
	}
}
