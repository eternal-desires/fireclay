package io.github.eternaldesires.fireclay.entity;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.component.SentinelVitals;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclayParticles;
import io.github.eternaldesires.fireclay.registry.FireclaySounds;
import io.github.eternaldesires.fireclay.registry.FireclayTags;

/**
 * The Terracotta Sentinel guard (design §3.6): a fired-clay golem that stands up from an effigy, keeps watch
 * around its post and fights hostile mobs. It never targets players.
 */
public class TerracottaSentinel extends PathfinderMob {
	/** Length of the awakening sequence in ticks. */
	public static final int AWAKEN_TICKS = 60;
	/** Length of the arm-swing animation in ticks (iron golem style). */
	public static final int ATTACK_ANIMATION_TICKS = 10;
	/** The sentinel walks back once it is farther than this from its post. */
	public static final double POST_RADIUS = 12.0;
	/** Hostiles are only engaged within this distance of the post. */
	public static final double GUARD_RADIUS = 16.0;

	private static final byte EVENT_ATTACK = 4;
	private static final float CLAY_REPAIR = 10.0f;
	private static final float FIRECLAY_REPAIR = 20.0f;
	private static final int REGEN_INTERVAL = 40;
	private static final int REGEN_HURT_COOLDOWN = 100;

	private static final EntityDataAccessor<Integer> DATA_AWAKEN =
			SynchedEntityData.defineId(TerracottaSentinel.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_MASTERWORK =
			SynchedEntityData.defineId(TerracottaSentinel.class, EntityDataSerializers.BOOLEAN);

	private @Nullable UUID owner;
	private @Nullable BlockPos post;
	private Optional<Masterwork> masterwork = Optional.empty();
	private int attackAnimationTick;
	private int lastHurtTick = -REGEN_HURT_COOLDOWN;

	public TerracottaSentinel(EntityType<? extends TerracottaSentinel> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, SentinelVitals.BASE_MAX_HEALTH)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	// ---- Awakening ------------------------------------------------------------------------------------

	/**
	 * Prepares a freshly created sentinel before it is added to the level: owner, post, masterwork record,
	 * health (from {@code fireclay:sentinel_vitals} when the effigy carries one) and the awakening countdown.
	 */
	public void awaken(@Nullable Player owner, BlockPos post, Optional<Masterwork> masterwork, @Nullable Float health) {
		this.owner = owner != null ? owner.getUUID() : null;
		this.setPost(post);
		this.setMasterwork(masterwork);
		float max = this.getMaxHealth();
		this.setHealth(health != null ? Mth.clamp(health, 1.0f, max) : max);
		this.entityData.set(DATA_AWAKEN, AWAKEN_TICKS);
		this.setPersistenceRequired();
	}

	/** Ticks left in the awakening sequence (60 → 0); 0 once awake. */
	public int getAwaken() {
		return this.entityData.get(DATA_AWAKEN);
	}

	public boolean isAwakening() {
		return this.getAwaken() > 0;
	}

	public boolean isMasterwork() {
		return this.entityData.get(DATA_MASTERWORK);
	}

	public Optional<Masterwork> getMasterwork() {
		return this.masterwork;
	}

	/** Remaining attack-swing ticks, for the renderer. */
	public int getAttackAnimationTick() {
		return this.attackAnimationTick;
	}

	public @Nullable UUID getOwnerId() {
		return this.owner;
	}

	/** Owner check; a sentinel without an owner (e.g. summoned by command) answers to anyone. */
	public boolean isOwnedBy(Player player) {
		return this.owner == null || this.owner.equals(player.getUUID());
	}

	public @Nullable BlockPos getPost() {
		return this.post;
	}

	public void setPost(BlockPos post) {
		this.post = post.immutable();
		// The home restriction keeps random strolls near the post; ReturnToPostGoal brings it back after a chase.
		this.setHomeTo(this.post, (int) GUARD_RADIUS);
	}

	private void setMasterwork(Optional<Masterwork> masterwork) {
		this.masterwork = masterwork;
		this.entityData.set(DATA_MASTERWORK, masterwork.isPresent());
		AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(SentinelVitals.maxHealth(masterwork.isPresent()));
		}
	}

	/** The effigy this sentinel turns back into when its owner picks it up. */
	public ItemStack toEffigy() {
		ItemStack effigy = new ItemStack(FireclayItems.SENTINEL_EFFIGY);
		effigy.set(FireclayComponents.SENTINEL_VITALS, new SentinelVitals(this.getHealth()));
		this.masterwork.ifPresent(record -> effigy.set(FireclayComponents.MASTERWORK, record));
		return effigy;
	}

	// ---- Synced and saved data -----------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_AWAKEN, 0);
		builder.define(DATA_MASTERWORK, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
		output.storeNullable("post", BlockPos.CODEC, this.post);
		this.masterwork.ifPresent(record -> output.store("masterwork", Masterwork.CODEC, record));
		output.putInt("awaken", this.getAwaken());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		input.read("post", BlockPos.CODEC).ifPresent(this::setPost);
		Optional<Masterwork> record = input.read("masterwork", Masterwork.CODEC);
		// Only touch the attribute when needed so a /summon with custom attributes keeps them.
		if (record.isPresent() || this.masterwork.isPresent()) {
			this.setMasterwork(record);
		}
		this.entityData.set(DATA_AWAKEN, Mth.clamp(input.getIntOr("awaken", 0), 0, AWAKEN_TICKS));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	// ---- AI --------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(2, new ReturnToPostGoal(this, POST_RADIUS, 1.0));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, TerracottaSentinel.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
				(target, level) -> this.isGuardTarget(target)));
	}

	/** Hostile, not on the {@code fireclay:sentinel_ignores} list, and within the guarded area around the post. */
	private boolean isGuardTarget(LivingEntity target) {
		if (!(target instanceof Enemy) || target.is(FireclayTags.EntityTypes.SENTINEL_IGNORES)) {
			return false;
		}
		return this.post == null || target.blockPosition().distSqr(this.post) <= GUARD_RADIUS * GUARD_RADIUS;
	}

	/** Never fight players or other sentinels, whoever started it. */
	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof Player) && !(target instanceof TerracottaSentinel) && super.canAttack(target);
	}

	/** Goals (including the target selector) do not run while the sentinel is still standing up. */
	@Override
	protected boolean isImmobile() {
		return this.isAwakening() || super.isImmobile();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			if (this.post == null) {
				// Sentinels summoned by command guard the spot where they first appear.
				this.setPost(this.blockPosition());
			}
			if (this.isAwakening()) {
				this.tickAwakening(level);
			} else if (this.isMasterwork() && this.isAlive() && this.tickCount % REGEN_INTERVAL == 0
					&& this.tickCount - this.lastHurtTick > REGEN_HURT_COOLDOWN && this.getHealth() < this.getMaxHealth()) {
				this.heal(1.0f);
			}
		}
	}

	private void tickAwakening(ServerLevel level) {
		int left = this.getAwaken();
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();
		level.sendParticles(ParticleTypes.LAVA, x, y + 0.1, z, 2, 0.35, 0.05, 0.35, 0.0);
		level.sendParticles(ParticleTypes.SMALL_FLAME, x, y + 0.2, z, 3, 0.35, 0.3, 0.35, 0.02);
		level.sendParticles(FireclayParticles.KILN_EMBER, x, y + 1.3, z, 1, 0.15, 0.1, 0.15, 0.01);
		if (left == AWAKEN_TICKS) {
			level.playSound(null, x, y, z, FireclaySounds.SENTINEL_AWAKEN, this.getSoundSource(), 1.0f, 1.0f);
		} else if (left == 20) {
			level.playSound(null, x, y, z, FireclaySounds.KILN_CRACK, this.getSoundSource(), 1.0f, 1.4f);
		}

		left--;
		this.entityData.set(DATA_AWAKEN, left);
		if (left == 0) {
			this.finishAwakening(level);
		}
	}

	private void finishAwakening(ServerLevel level) {
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();
		for (int i = 0; i < 24; i++) {
			double angle = i * (Math.PI * 2.0 / 24.0);
			level.sendParticles(ParticleTypes.FLAME, x + Math.cos(angle) * 0.9, y + 0.1, z + Math.sin(angle) * 0.9,
					1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.5, z, 6, 0.4, 0.2, 0.4, 0.01);

		ServerPlayer player = this.owner != null ? level.getServer().getPlayerList().getPlayer(this.owner) : null;
		if (player != null) {
			ItemStack effigy = new ItemStack(FireclayItems.SENTINEL_EFFIGY);
			this.masterwork.ifPresent(record -> effigy.set(FireclayComponents.MASTERWORK, record));
			FireclayCriteria.POTTER_ACTION.trigger(player, PotterActionTrigger.AWAKENED, effigy, this.isMasterwork());
		}
	}

	// ---- Combat ----------------------------------------------------------------------------------------

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (this.isAwakening() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.lastHurtTick = this.tickCount;
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.attackAnimationTick = ATTACK_ANIMATION_TICKS;
		level.broadcastEntityEvent(this, EVENT_ATTACK);
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			target.igniteForSeconds(4.0f);
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte event) {
		if (event == EVENT_ATTACK) {
			this.attackAnimationTick = ATTACK_ANIMATION_TICKS;
		} else {
			super.handleEntityEvent(event);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.attackAnimationTick > 0) {
			this.attackAnimationTick--;
		}
	}

	/** Fired clay does not breathe. */
	@Override
	protected int decreaseAirSupply(int air) {
		return air;
	}

	// ---- Interaction -----------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.isAwakening() || !this.isOwnedBy(player)) {
			return super.mobInteract(player, hand);
		}
		ItemStack stack = player.getItemInHand(hand);

		float repair = stack.is(Items.CLAY_BALL) ? CLAY_REPAIR : stack.is(FireclayItems.FIRECLAY_BALL) ? FIRECLAY_REPAIR : 0.0f;
		if (repair > 0.0f) {
			if (this.getHealth() >= this.getMaxHealth()) {
				return InteractionResult.PASS;
			}
			if (this.level() instanceof ServerLevel level) {
				this.heal(repair);
				stack.consume(1, player);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(),
						4, 0.4, 0.6, 0.4, 0.0);
				this.playSound(SoundEvents.DECORATED_POT_INSERT, 1.0f, 0.8f);
			}
			return InteractionResult.SUCCESS;
		}

		if (stack.isEmpty() && player.isShiftKeyDown()) {
			if (this.level() instanceof ServerLevel level) {
				ItemStack effigy = this.toEffigy();
				if (!player.addItem(effigy)) {
					this.spawnAtLocation(level, effigy);
				}
				this.playSound(SoundEvents.DECORATED_POT_INSERT, 1.0f, 0.6f);
				this.discard();
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	// ---- Bookkeeping, sounds and drops -----------------------------------------------------------------

	@Override
	public void remove(Entity.RemovalReason reason) {
		// Killed or picked up: free the slot in the owner's per-player cap.
		if (reason.shouldDestroy() && this.owner != null && this.level() instanceof ServerLevel level) {
			SentinelRoster.get(level.getServer()).remove(this.owner, this.getUUID());
		}
		super.remove(reason);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DECORATED_POT_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DECORATED_POT_SHATTER;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.STONE_STEP, 0.5f, 0.8f);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(FireclayItems.KILN_SHARD, 4 + this.random.nextInt(5)));
	}
}
