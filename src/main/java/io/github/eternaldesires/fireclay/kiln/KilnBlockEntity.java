package io.github.eternaldesires.fireclay.kiln;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.attachment.PotterRecord;
import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.network.KilnReadingPayload;
import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayBlocks;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayGameRules;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclayRecipes;
import io.github.eternaldesires.fireclay.registry.FireclaySounds;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/**
 * The kiln's heat simulation and inventory (design §2.2, §3.4). The public methods listed in docs/api.md are a
 * cross-feature contract and keep their signatures.
 *
 * <p>Each server tick: poll the damper, resolve the firing recipe, ignite fuel if a firing can run, integrate the
 * heat {@code H += P - k(H - 20)}, then advance the firing (progress, sweet spot, overheat, crack or completion).
 */
public class KilnBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int SLOT_GREENWARE = 0;
	public static final int SLOT_GLAZE = 1;
	public static final int SLOT_FUEL = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SLOT_COUNT = 4;

	public static final double AMBIENT_HEAT = 20.0;
	public static final int CAP_UNLINED = 750;
	public static final int CAP_LINED = 1000;
	/** Heat added by one bellows pump, and the burn ticks it costs. */
	public static final double PUMP_HEAT = 35.0;
	public static final int PUMP_BURN_COST = 20;

	/** Heat loss per tick per degree above ambient. */
	private static final double LOSS_UNLINED = 0.004;
	private static final double LOSS_LINED = 0.0032;
	/** Heat at which the white_heat action fires (once per firing). */
	private static final double WHITE_HEAT = 950.0;
	/** Masterwork needs this share of the firing time spent in the sweet spot. */
	private static final double MASTERWORK_SHARE = 0.9;
	private static final double THERMOSTAT_RADIUS = 16.0;

	private static final int[] SLOTS_UP = {SLOT_GREENWARE};
	private static final int[] SLOTS_DOWN = {SLOT_OUTPUT, SLOT_FUEL};
	private static final int[] SLOTS_SIDES = {SLOT_GLAZE, SLOT_FUEL};

	/** A fuel tier: an item tag, its heat power per tick and how long one item burns. */
	private record FuelTier(TagKey<Item> tag, double power, int burnTicks) {
	}

	/** Checked in this order; the kiln deliberately ignores vanilla fuel values (design §2.2, risk R3). */
	private static final List<FuelTier> FUEL_TIERS = List.of(
			new FuelTier(FireclayTags.Items.KILN_FUEL_BLAZE, 3.0, 2400),
			new FuelTier(FireclayTags.Items.KILN_FUEL_LAVA, 2.6, 20000),
			new FuelTier(FireclayTags.Items.KILN_FUEL_COAL_BLOCK, 2.4, 16000),
			new FuelTier(FireclayTags.Items.KILN_FUEL_COAL, 2.0, 1600),
			new FuelTier(FireclayTags.Items.KILN_FUEL_WOOD, 1.2, 300),
			new FuelTier(FireclayTags.Items.KILN_FUEL_KINDLING, 1.2, 100));

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private final RecipeManager.CachedCheck<FiringInput, FiringRecipe> recipeCheck = RecipeManager.createCheck(FireclayRecipes.FIRING);

	// Saved state.
	private double heat = AMBIENT_HEAT;
	private int burn;
	private int burnMax;
	private double power;
	private int progress;
	private int sweet;
	private int over;
	private @Nullable ResourceKey<Recipe<?>> recipeId;
	private int manualPumps;
	private int redstonePumps;
	private int damperToggles;
	private boolean whiteHeatReached;
	private @Nullable UUID stokerId;
	private @Nullable String stokerName;
	private int lastSignal;

	// Server-side cache of the matched recipe (recipes are not synced; the client sees ContainerData only).
	private @Nullable FiringRecipe recipe;
	private boolean resultFits;
	private boolean resultEligible;

	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case KilnMenu.DATA_HEAT -> (int) Math.round(heat);
				case KilnMenu.DATA_CAP -> getCap();
				case KilnMenu.DATA_WINDOW_MIN -> getWindowMin();
				case KilnMenu.DATA_WINDOW_MAX -> getWindowMax();
				case KilnMenu.DATA_PROGRESS -> progress;
				case KilnMenu.DATA_TOTAL -> getTotal();
				case KilnMenu.DATA_BURN -> burn;
				case KilnMenu.DATA_BURN_MAX -> burnMax;
				case KilnMenu.DATA_FLAGS -> getFlags();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Server-authoritative: the client copy is a SimpleContainerData.
		}

		@Override
		public int getCount() {
			return KilnMenu.DATA_COUNT;
		}
	};

	public KilnBlockEntity(BlockPos pos, BlockState state) {
		super(FireclayBlockEntities.KILN, pos, state);
	}

	// ---------------------------------------------------------------- simulation

	/** Server ticker, also called directly by gametests: {@code KilnBlockEntity.serverTick(level, pos, state, kiln)}. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, KilnBlockEntity kiln) {
		if (level instanceof ServerLevel serverLevel) {
			kiln.tick(serverLevel, pos);
		}
	}

	private void tick(ServerLevel level, BlockPos pos) {
		// Gametests may pass a stale state; the block entity's own copy is kept current by the chunk.
		BlockState state = getBlockState();
		if (!state.is(FireclayBlocks.KILN)) {
			return;
		}
		state = updateDamper(level, pos, state);
		refreshRecipe(level);
		boolean active = recipe != null && resultFits;

		if (!isBurning() && active) {
			ignite();
		}

		double p = 0.0;
		if (isBurning()) {
			p = power;
			burn--;
		}
		double k = isLined() ? LOSS_LINED : LOSS_UNLINED;
		if (isDamperOpen()) {
			p *= 0.5;
			k *= 2.0;
		}
		heat = Mth.clamp(heat + p - k * (heat - AMBIENT_HEAT), AMBIENT_HEAT, getCap());
		if (heat < AMBIENT_HEAT + 0.01) {
			heat = AMBIENT_HEAT;
		}

		if (active) {
			fire(level, pos, state);
		}

		if (recipe != null && !whiteHeatReached && heat >= WHITE_HEAT) {
			whiteHeatReached = true;
			ServerPlayer stoker = onlineStoker(level);
			if (stoker != null) {
				FireclayCriteria.POTTER_ACTION.trigger(stoker, PotterActionTrigger.WHITE_HEAT);
			}
		}

		state = updateHeatStage(level, pos, getBlockState());
		ambience(level, pos, state);
		updateComparator(level, pos, state);
		if (isBurning() || heat > AMBIENT_HEAT || recipe != null) {
			// Mark the chunk dirty without the neighbour update that setChanged() would cause every tick.
			level.blockEntityChanged(pos);
		}
	}

	/** The damper is open while redstone enters the back or top face (design §2.2). */
	private BlockState updateDamper(ServerLevel level, BlockPos pos, BlockState state) {
		Direction back = state.getValue(KilnBlock.FACING).getOpposite();
		boolean open = level.hasSignal(pos.relative(Direction.UP), Direction.UP) || level.hasSignal(pos.relative(back), back);
		if (open == state.getValue(KilnBlock.DAMPER)) {
			return state;
		}
		if (recipe != null) {
			damperToggles++;
		}
		BlockState updated = state.setValue(KilnBlock.DAMPER, open);
		level.setBlock(pos, updated, Block.UPDATE_ALL);
		return updated;
	}

	private void ignite() {
		ItemStack fuel = items.get(SLOT_FUEL);
		FuelTier tier = fuelTier(fuel);
		if (tier == null) {
			return;
		}
		burn = tier.burnTicks();
		burnMax = tier.burnTicks();
		power = tier.power();
		if (fuel.is(Items.LAVA_BUCKET)) {
			items.set(SLOT_FUEL, new ItemStack(Items.BUCKET));
		} else {
			fuel.shrink(1);
		}
	}

	/** One tick of an active firing: window progress, sweet spot, overheat, then crack or completion. */
	private void fire(ServerLevel level, BlockPos pos, BlockState state) {
		FiringRecipe current = Objects.requireNonNull(recipe);
		if (heat > current.maxHeat()) {
			over++;
		} else {
			over = Math.max(0, over - 2);
			if (heat >= current.minHeat()) {
				progress++;
				if (heat >= current.sweetMin() && heat <= current.sweetMax()) {
					sweet++;
				}
			}
		}

		if (over >= FireclayGameRules.crackGrace(level)) {
			crack(level, pos, state.getValue(KilnBlock.FACING));
		} else if (progress >= current.time()) {
			complete(level, pos, state.getValue(KilnBlock.FACING), current);
		}
	}

	private void complete(ServerLevel level, BlockPos pos, Direction facing, FiringRecipe current) {
		ItemStack result = current.assemble(new FiringInput(items.get(SLOT_GREENWARE), items.get(SLOT_GLAZE)));
		Vec3 mouth = mouth(pos, facing);
		ServerPlayer stoker = onlineStoker(level);
		boolean masterwork = result.is(FireclayTags.Items.MASTERWORK_ELIGIBLE) && sweet >= MASTERWORK_SHARE * current.time();

		if (masterwork) {
			result.set(FireclayComponents.MASTERWORK, Masterwork.signedBy(stokerName));
			level.playSound(null, pos, FireclaySounds.KILN_MASTERWORK, SoundSource.BLOCKS, 1.0f, 1.0f);
			level.sendParticles(ParticleTypes.WAX_ON, mouth.x, mouth.y, mouth.z, 12, 0.3, 0.3, 0.3, 0.1);
			if (stoker != null) {
				FireclayCriteria.POTTER_ACTION.trigger(stoker, PotterActionTrigger.MASTERWORK, result, true);
				PotterRecord.recordMasterwork(stoker);
			}
			if (manualPumps == 0 && redstonePumps + damperToggles > 0) {
				for (ServerPlayer player : PlayerLookup.around(level, Vec3.atCenterOf(pos), THERMOSTAT_RADIUS)) {
					FireclayCriteria.POTTER_ACTION.trigger(player, PotterActionTrigger.THERMOSTAT, result, true);
				}
			}
		}

		ItemStack fired = result.copy();
		deliver(level, mouth, result);
		items.get(SLOT_GREENWARE).shrink(1);
		boolean glazed = current.glaze().isPresent();
		if (glazed) {
			items.get(SLOT_GLAZE).shrink(1);
		}

		level.playSound(null, pos, FireclaySounds.KILN_PING, SoundSource.BLOCKS, glazed ? 1.0f : 0.5f, 1.0f);
		if (stoker != null) {
			FireclayCriteria.POTTER_ACTION.trigger(stoker, PotterActionTrigger.FIRED, fired, masterwork);
			PotterRecord.recordFiring(stoker);
		}
		resetFiring();
		setChanged();
	}

	private void crack(ServerLevel level, BlockPos pos, Direction facing) {
		Vec3 mouth = mouth(pos, facing);
		items.get(SLOT_GREENWARE).shrink(1);
		if (!items.get(SLOT_GLAZE).isEmpty()) {
			items.get(SLOT_GLAZE).shrink(1);
		}
		deliver(level, mouth, new ItemStack(FireclayItems.KILN_SHARD, 2 + level.getRandom().nextInt(3)));

		level.playSound(null, pos, FireclaySounds.KILN_CRACK, SoundSource.BLOCKS, 1.0f, 0.8f + level.getRandom().nextFloat() * 0.2f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, FireclayItems.KILN_SHARD), mouth.x, mouth.y, mouth.z,
				10, 0.2, 0.2, 0.2, 0.08);
		level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 6, 0.2, 0.2, 0.2, 0.02);

		ServerPlayer stoker = onlineStoker(level);
		if (stoker != null) {
			FireclayCriteria.POTTER_ACTION.trigger(stoker, PotterActionTrigger.CRACKED);
			PotterRecord.recordCrack(stoker);
		}
		resetFiring();
		setChanged();
	}

	/** Puts {@code stack} into the output slot as far as it merges; the rest drops at the kiln mouth. */
	private void deliver(ServerLevel level, Vec3 mouth, ItemStack stack) {
		ItemStack out = items.get(SLOT_OUTPUT);
		if (out.isEmpty()) {
			int move = Math.min(stack.getCount(), stack.getMaxStackSize());
			items.set(SLOT_OUTPUT, stack.split(move));
		} else if (ItemStack.isSameItemSameComponents(out, stack)) {
			int move = Math.min(stack.getCount(), out.getMaxStackSize() - out.getCount());
			if (move > 0) {
				out.grow(move);
				stack.shrink(move);
			}
		}
		if (!stack.isEmpty()) {
			Containers.dropItemStack(level, mouth.x, mouth.y, mouth.z, stack);
		}
	}

	/** Clears the per-firing counters (after a completion, a crack or a recipe change). */
	private void resetFiring() {
		progress = 0;
		sweet = 0;
		over = 0;
		manualPumps = 0;
		redstonePumps = 0;
		damperToggles = 0;
		whiteHeatReached = false;
	}

	/**
	 * Re-resolves the recipe for the current slots. A different recipe id (including none) resets the firing.
	 * The result must fit the output slot for the firing to run, as in a furnace.
	 */
	private void refreshRecipe(ServerLevel level) {
		ItemStack greenware = items.get(SLOT_GREENWARE);
		Optional<RecipeHolder<FiringRecipe>> match = greenware.isEmpty()
				? Optional.empty()
				: recipeCheck.getRecipeFor(new FiringInput(greenware, items.get(SLOT_GLAZE)), level);
		ResourceKey<Recipe<?>> id = match.map(RecipeHolder::id).orElse(null);
		if (!Objects.equals(id, recipeId)) {
			recipeId = id;
			resetFiring();
		}
		recipe = match.map(RecipeHolder::value).orElse(null);
		if (recipe == null) {
			resultFits = false;
			resultEligible = false;
			return;
		}
		ItemStack preview = recipe.result().create();
		resultEligible = preview.is(FireclayTags.Items.MASTERWORK_ELIGIBLE);
		ItemStack out = items.get(SLOT_OUTPUT);
		resultFits = out.isEmpty()
				|| (ItemStack.isSameItemSameComponents(out, preview) && out.getCount() + preview.getCount() <= out.getMaxStackSize());
	}

	private BlockState updateHeatStage(ServerLevel level, BlockPos pos, BlockState state) {
		int stage = heatStage(heat);
		if (stage == state.getValue(KilnBlock.HEAT)) {
			return state;
		}
		BlockState updated = state.setValue(KilnBlock.HEAT, stage);
		level.setBlock(pos, updated, Block.UPDATE_ALL);
		return updated;
	}

	/** Visual heat stage: 0 below 100, 1 below 400, 2 below 700, 3 from 700 (white-hot). */
	public static int heatStage(double heat) {
		if (heat < 100) {
			return 0;
		}
		if (heat < 400) {
			return 1;
		}
		return heat < 700 ? 2 : 3;
	}

	private void ambience(ServerLevel level, BlockPos pos, BlockState state) {
		long time = level.getGameTime();
		int stage = state.getValue(KilnBlock.HEAT);
		if (stage >= 2 && time % 40 == 0) {
			level.playSound(null, pos, FireclaySounds.KILN_ROAR, SoundSource.BLOCKS, 0.4f + 0.2f * stage, 1.0f);
		}
		if (over > 0 && time % 10 == 0) {
			level.playSound(null, pos, FireclaySounds.KILN_OVERHEAT, SoundSource.BLOCKS, 0.8f, 1.0f);
			level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.0);
		}
	}

	private void updateComparator(ServerLevel level, BlockPos pos, BlockState state) {
		int signal = getComparatorSignal();
		if (signal != lastSignal) {
			lastSignal = signal;
			level.updateNeighbourForOutputSignal(pos, state.getBlock());
		}
	}

	private static Vec3 mouth(BlockPos pos, Direction facing) {
		return Vec3.atCenterOf(pos).add(facing.getStepX() * 0.6, -0.1, facing.getStepZ() * 0.6);
	}

	private @Nullable ServerPlayer onlineStoker(ServerLevel level) {
		return stokerId == null ? null : level.getServer().getPlayerList().getPlayer(stokerId);
	}

	private static @Nullable FuelTier fuelTier(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (FuelTier tier : FUEL_TIERS) {
			if (stack.is(tier.tag())) {
				return tier;
			}
		}
		return null;
	}

	/** True for anything the fuel slot accepts: a tier tag or {@code #fireclay:kiln_fuels}. */
	public static boolean isFuel(ItemStack stack) {
		return stack.is(FireclayTags.Items.KILN_FUELS) || fuelTier(stack) != null;
	}

	// ---------------------------------------------------------------- cross-feature API (docs/api.md §4.2)

	/**
	 * One bellows pump. While burning: +{@value #PUMP_HEAT} heat (clamped to the cap) and −{@value #PUMP_BURN_COST}
	 * burn ticks, and returns true. Otherwise only an ash puff, and returns false. {@code stoker} is the player for a
	 * manual pump (counted as manual and recorded as stoker) or null for a redstone pump.
	 */
	public boolean pump(ServerLevel level, @Nullable Player stoker) {
		if (!isBurning()) {
			Vec3 mouth = mouth(worldPosition, getBlockState().getValue(KilnBlock.FACING));
			level.sendParticles(ParticleTypes.ASH, mouth.x, mouth.y, mouth.z, 8, 0.2, 0.2, 0.2, 0.02);
			level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 3, 0.1, 0.1, 0.1, 0.01);
			return false;
		}
		heat = Math.min(getCap(), heat + PUMP_HEAT);
		burn = Math.max(0, burn - PUMP_BURN_COST);
		if (stoker != null) {
			manualPumps++;
			setStoker(stoker);
		} else {
			redstonePumps++;
		}
		setChanged();
		return true;
	}

	public double getHeat() {
		return heat;
	}

	/** Sets the heat directly (commands, tests, creative); clamped to [{@value #AMBIENT_HEAT}, cap]. */
	public void setHeat(double value) {
		heat = Mth.clamp(value, AMBIENT_HEAT, getCap());
		if (level instanceof ServerLevel serverLevel) {
			refreshRecipe(serverLevel);
		}
		setChanged();
	}

	public int getCap() {
		return isLined() ? CAP_LINED : CAP_UNLINED;
	}

	public boolean isLined() {
		return getBlockState().getValue(KilnBlock.LINED);
	}

	public boolean isDamperOpen() {
		return getBlockState().getValue(KilnBlock.DAMPER);
	}

	public boolean isBurning() {
		return burn > 0;
	}

	public int getBurn() {
		return burn;
	}

	public int getBurnMax() {
		return burnMax;
	}

	/** True when a firing recipe matches the current slots (server side). */
	public boolean hasRecipe() {
		return recipe != null;
	}

	/** Current recipe's min_heat, or 0 without a recipe. */
	public int getWindowMin() {
		return recipe == null ? 0 : recipe.minHeat();
	}

	/** Current recipe's max_heat, or 0 without a recipe. */
	public int getWindowMax() {
		return recipe == null ? 0 : recipe.maxHeat();
	}

	/** In-window ticks fired so far. */
	public int getProgress() {
		return progress;
	}

	/** The recipe's time, or 0 without a recipe. */
	public int getTotal() {
		return recipe == null ? 0 : recipe.time();
	}

	/** In-window ticks spent in the sweet spot. */
	public int getSweet() {
		return sweet;
	}

	/** The overheat counter compared against the kiln_crack_grace game rule. */
	public int getOver() {
		return over;
	}

	/** Window-relative comparator output (design §2.2): 0 none/below, 1..14 inside, 15 above. */
	public int getComparatorSignal() {
		if (recipe == null || heat < recipe.minHeat()) {
			return 0;
		}
		if (heat > recipe.maxHeat()) {
			return 15;
		}
		int width = Math.max(1, recipe.maxHeat() - recipe.minHeat());
		return 1 + Math.min(13, Mth.floor(14.0 * (heat - recipe.minHeat()) / width));
	}

	/** {@link KilnMenu} {@code FLAG_*} bits. */
	public int getFlags() {
		int flags = 0;
		if (over > 0) {
			flags |= KilnMenu.FLAG_OVER;
		}
		if (recipe != null && heat >= recipe.sweetMin() && heat <= recipe.sweetMax()) {
			flags |= KilnMenu.FLAG_IN_SWEET;
		}
		if (isDamperOpen()) {
			flags |= KilnMenu.FLAG_DAMPER;
		}
		if (isLined()) {
			flags |= KilnMenu.FLAG_LINED;
		}
		if (recipe != null && resultEligible && progress > 0 && sweet >= MASTERWORK_SHARE * progress) {
			flags |= KilnMenu.FLAG_MASTERWORK_ON_TRACK;
		}
		return flags;
	}

	/** Records the player who last tended the kiln; criteria and the potter's mark go to them. */
	public void setStoker(Player player) {
		stokerId = player.getUUID();
		stokerName = player.getName().getString();
		setChanged();
	}

	public @Nullable String getStokerName() {
		return stokerName;
	}

	public ContainerData getContainerData() {
		return dataAccess;
	}

	/** A Pyrometer reading of this kiln. */
	public KilnReadingPayload reading() {
		return KilnReadingPayload.of(getBlockPos(), (int) Math.round(heat), getCap(), getWindowMin(), getWindowMax(),
				progress, getTotal(), getFlags());
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		heat = input.getDoubleOr("heat", AMBIENT_HEAT);
		burn = input.getIntOr("burn", 0);
		burnMax = input.getIntOr("burn_max", 0);
		power = input.getDoubleOr("power", 0.0);
		progress = input.getIntOr("progress", 0);
		sweet = input.getIntOr("sweet", 0);
		over = input.getIntOr("over", 0);
		recipeId = input.read("recipe", Recipe.KEY_CODEC).orElse(null);
		manualPumps = input.getIntOr("manual_pumps", 0);
		redstonePumps = input.getIntOr("redstone_pumps", 0);
		damperToggles = input.getIntOr("damper_toggles", 0);
		whiteHeatReached = input.getBooleanOr("white_heat", false);
		stokerId = input.read("stoker", UUIDUtil.CODEC).orElse(null);
		stokerName = input.getString("stoker_name").orElse(null);
		lastSignal = input.getIntOr("last_signal", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putDouble("heat", heat);
		output.putInt("burn", burn);
		output.putInt("burn_max", burnMax);
		output.putDouble("power", power);
		output.putInt("progress", progress);
		output.putInt("sweet", sweet);
		output.putInt("over", over);
		output.storeNullable("recipe", Recipe.KEY_CODEC, recipeId);
		output.putInt("manual_pumps", manualPumps);
		output.putInt("redstone_pumps", redstonePumps);
		output.putInt("damper_toggles", damperToggles);
		output.putBoolean("white_heat", whiteHeatReached);
		output.storeNullable("stoker", UUIDUtil.CODEC, stokerId);
		if (stokerName != null) {
			output.putString("stoker_name", stokerName);
		}
		output.putInt("last_signal", lastSignal);
	}

	/** Spills the contents when the block goes (risk R9); a no-op if the default already emptied the slots. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null) {
			Containers.dropContents(level, pos, this);
		}
	}

	// ---------------------------------------------------------------- container

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.fireclay.kiln");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new KilnMenu(containerId, inventory, this, dataAccess);
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		super.setItem(slot, stack);
		if (level instanceof ServerLevel serverLevel) {
			refreshRecipe(serverLevel);
		}
	}

	/** Slot rules shared by hoppers and the menu: greenware, glazes, fuels; the output is take-only. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case SLOT_GREENWARE -> stack.is(FireclayTags.Items.GREENWARE);
			case SLOT_GLAZE -> stack.is(FireclayTags.Items.GLAZES);
			case SLOT_FUEL -> isFuel(stack);
			default -> false;
		};
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return switch (side) {
			case UP -> SLOTS_UP;
			case DOWN -> SLOTS_DOWN;
			default -> SLOTS_SIDES;
		};
	}

	/** Top face: greenware. Side faces: glaze if it is one, otherwise fuel. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		if (!canPlaceItem(slot, stack)) {
			return false;
		}
		if (side == Direction.UP) {
			return slot == SLOT_GREENWARE;
		}
		if (side == Direction.DOWN) {
			return false;
		}
		return slot == SLOT_GLAZE || !stack.is(FireclayTags.Items.GLAZES);
	}

	/** Bottom face: the output, and the empty bucket left by lava. */
	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && (slot == SLOT_OUTPUT || (slot == SLOT_FUEL && stack.is(Items.BUCKET)));
	}
}
