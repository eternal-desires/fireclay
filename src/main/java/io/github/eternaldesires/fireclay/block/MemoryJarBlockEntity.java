package io.github.eternaldesires.fireclay.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.component.StoredXp;
import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;

/**
 * Stored experience of a Memory Jar (design §3.2). The XP and the potter's mark travel with the item as
 * {@code fireclay:stored_xp} and {@code fireclay:masterwork}. Owned by the ceramics feature.
 */
public class MemoryJarBlockEntity extends BlockEntity {
	/** Total XP to reach level 30. */
	public static final int CAPACITY = 1395;
	public static final int MASTERWORK_CAPACITY = 4185;

	private int xp;
	/** The masterwork component of the placed item (keeps the potter's name); null for plain jars. */
	private @Nullable Masterwork masterwork;

	public MemoryJarBlockEntity(BlockPos pos, BlockState state) {
		super(FireclayBlockEntities.MEMORY_JAR, pos, state);
	}

	public int getXp() {
		return xp;
	}

	/** Sets the stored XP, clamped to [0, capacity], and updates the {@code fill} blockstate. */
	public void setXp(int value) {
		xp = Math.max(0, Math.min(value, getCapacity()));
		setChanged();
		updateFill();
	}

	public boolean isMasterwork() {
		return getBlockState().getValue(MemoryJarBlock.MASTERWORK);
	}

	public int getCapacity() {
		return isMasterwork() ? MASTERWORK_CAPACITY : CAPACITY;
	}

	/** ceil(4 × xp / capacity). */
	public static int fillFor(int xp, int capacity) {
		return xp <= 0 ? 0 : Math.min(4, (4 * xp + capacity - 1) / capacity);
	}

	/** round(15 × xp / capacity). */
	public int getComparatorSignal() {
		return Math.round(15.0f * xp / getCapacity());
	}

	private void updateFill() {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(worldPosition);
		if (!state.hasProperty(MemoryJarBlock.FILL)) {
			return;
		}
		int fill = fillFor(xp, getCapacity());
		if (state.getValue(MemoryJarBlock.FILL) != fill) {
			level.setBlock(worldPosition, state.setValue(MemoryJarBlock.FILL, fill), Block.UPDATE_ALL);
		}
	}

	// --- Player experience ---------------------------------------------------------------------------------------

	/** Points from level {@code level} to the next; the same formula as {@link Player#getXpNeededForNextLevel()}. */
	public static int xpNeeded(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** Points already earned towards the next level. */
	private static int partialPoints(Player player) {
		int need = xpNeeded(player.experienceLevel);
		return Math.max(0, Math.min(need - 1, Math.round(player.experienceProgress * need)));
	}

	private static int totalPoints(Player player) {
		int total = 0;
		for (int level = 0; level < player.experienceLevel; level++) {
			total += xpNeeded(level);
		}
		return total + partialPoints(player);
	}

	/**
	 * Moves the player to exactly {@code total} points with integer math (negative
	 * {@code giveExperiencePoints} can overshoot a level through float rounding).
	 */
	private static void setTotalPoints(Player player, int total, int delta) {
		int level = 0;
		int remainder = Math.max(0, total);
		while (remainder >= xpNeeded(level)) {
			remainder -= xpNeeded(level);
			level++;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			// The setters also force the experience bar to resync.
			serverPlayer.setExperienceLevels(level);
			serverPlayer.setExperiencePoints(remainder);
		} else {
			player.experienceLevel = level;
			player.experienceProgress = (float) remainder / xpNeeded(level);
		}
		player.totalExperience = Math.max(0, player.totalExperience + delta);
	}

	/**
	 * Deposits the player's partial level, or a whole level when the bar is empty, clamped to the free space.
	 *
	 * @return the points moved into the jar (0 if nothing moved)
	 */
	public int deposit(Player player) {
		int partial = partialPoints(player);
		int amount = partial > 0 ? partial : player.experienceLevel > 0 ? xpNeeded(player.experienceLevel - 1) : 0;
		amount = Math.min(amount, getCapacity() - xp);
		if (amount <= 0) {
			return 0;
		}
		setTotalPoints(player, totalPoints(player) - amount, -amount);
		setXp(xp + amount);
		return amount;
	}

	/**
	 * Withdraws enough to complete the player's current level, limited by what the jar holds.
	 *
	 * @return the points given to the player (0 if nothing moved)
	 */
	public int withdraw(Player player) {
		int amount = Math.min(xp, xpNeeded(player.experienceLevel) - partialPoints(player));
		if (amount <= 0) {
			return 0;
		}
		setTotalPoints(player, totalPoints(player) + amount, amount);
		setXp(xp - amount);
		return amount;
	}

	public boolean isFull() {
		return xp >= getCapacity();
	}

	// --- Persistence and item components -------------------------------------------------------------------------

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		xp = Math.max(0, input.getIntOr("xp", 0));
		masterwork = input.read("masterwork", Masterwork.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("xp", xp);
		output.storeNullable("masterwork", Masterwork.CODEC, masterwork);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		xp = Math.max(0, Math.min(components.getOrDefault(FireclayComponents.STORED_XP, StoredXp.EMPTY).points(), getCapacity()));
		masterwork = components.get(FireclayComponents.MASTERWORK);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (xp > 0) {
			components.set(FireclayComponents.STORED_XP, new StoredXp(xp));
		}
		if (masterwork != null) {
			components.set(FireclayComponents.MASTERWORK, masterwork);
		} else if (isMasterwork()) {
			components.set(FireclayComponents.MASTERWORK, Masterwork.UNSIGNED);
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		super.removeComponentsFromTag(output);
		output.discard("xp");
		output.discard("masterwork");
	}
}
