package io.github.eternaldesires.fireclay.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.eternaldesires.fireclay.advancement.PotterActionTrigger;
import io.github.eternaldesires.fireclay.attachment.PotterRecord;
import io.github.eternaldesires.fireclay.primer.PottersPrimer;
import io.github.eternaldesires.fireclay.registry.FireclayCriteria;
import io.github.eternaldesires.fireclay.registry.FireclayItems;
import io.github.eternaldesires.fireclay.registry.FireclaySounds;

/**
 * The potter's wheel (contract §1, design §3.2). Load it with clay or fireclay balls, sneak-use with an empty hand to
 * pick a shape, then use it with an empty hand five times to throw the piece. Owned by the workshop feature.
 */
public class PottersWheelBlock extends BaseEntityBlock {
	/** Size of the clay lump on the head: ceil(loaded balls / 2). */
	public static final IntegerProperty CLAY = IntegerProperty.create("clay", 0, 4);
	/** True when the loaded clay is fireclay. */
	public static final BooleanProperty FIRECLAY = BooleanProperty.create("fireclay");
	public static final BooleanProperty SPINNING = BooleanProperty.create("spinning");

	/** Ticks the head spins after a throw; further throws are ignored meanwhile. */
	public static final int SPIN_TICKS = 10;
	/** Height of the wheel head's top face, in blocks. */
	private static final double HEAD_TOP = 12.0 / 16.0;

	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(0, 0, 0, 16, 3, 16),
			Block.box(6, 3, 6, 10, 10, 10),
			Block.box(1, 10, 1, 15, 12, 15));

	public PottersWheelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(CLAY, 0)
				.setValue(FIRECLAY, false)
				.setValue(SPINNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CLAY, FIRECLAY, SPINNING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PottersWheelBlockEntity(pos, state);
	}

	/** Loading: one clay or fireclay ball per use. Anything else falls through to the empty-hand interaction. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		boolean clay = stack.is(Items.CLAY_BALL);
		boolean fireclay = stack.is(FireclayItems.FIRECLAY_BALL);
		if (!clay && !fireclay) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level.getBlockEntity(pos) instanceof PottersWheelBlockEntity wheel)) {
			return InteractionResult.PASS;
		}
		if (wheel.getCount() > 0 && wheel.isFireclay() != fireclay) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.wheel.no_mix"));
			return InteractionResult.FAIL;
		}
		if (wheel.getCount() >= PottersWheelBlockEntity.MAX_CLAY) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.wheel.full", PottersWheelBlockEntity.MAX_CLAY));
			return InteractionResult.FAIL;
		}

		wheel.addClay(fireclay);
		stack.consume(1, player);
		level.setBlock(pos, wheel.withLump(state), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 0.6f, 1.1f + level.getRandom().nextFloat() * 0.2f);
		sendShaping(player, wheel);
		return InteractionResult.SUCCESS;
	}

	/** Empty hand: a throw. Sneaking with an empty hand: pick the next shape. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof PottersWheelBlockEntity wheel)) {
			return InteractionResult.PASS;
		}

		if (player.isSecondaryUseActive()) {
			wheel.cycleShape();
			sendShaping(player, wheel);
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(SPINNING)) {
			// Still turning from the last throw.
			return InteractionResult.CONSUME;
		}

		PottersWheelBlockEntity.Shape shape = wheel.getShape();
		if (shape.needsFireclay() && (wheel.getCount() == 0 || !wheel.isFireclay())) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.wheel.needs_fireclay",
					Component.translatable(shape.item().getDescriptionId())));
			return InteractionResult.FAIL;
		}
		if (wheel.getCount() < shape.cost()) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.wheel.needs", shape.cost()));
			return InteractionResult.FAIL;
		}

		throwClay(serverLevel, pos, state, player, wheel);
		return InteractionResult.SUCCESS;
	}

	private void throwClay(ServerLevel level, BlockPos pos, BlockState state, Player player, PottersWheelBlockEntity wheel) {
		Item ball = wheel.loadedBall();
		PottersWheelBlockEntity.Shape shape = wheel.getShape();
		boolean finished = wheel.addThrow();

		level.setBlock(pos, wheel.withLump(state).setValue(SPINNING, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, this, SPIN_TICKS);

		double x = pos.getX() + 0.5;
		double y = pos.getY() + HEAD_TOP + 0.1;
		double z = pos.getZ() + 0.5;
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ball), x, y, z, 8, 0.2, 0.05, 0.2, 0.08);
		RandomSource random = level.getRandom();
		level.playSound(null, pos, FireclaySounds.POTTERS_WHEEL_THROW, SoundSource.BLOCKS, 1.0f, 0.9f + random.nextFloat() * 0.2f);

		if (!finished) {
			player.sendOverlayMessage(Component.translatable("message.fireclay.wheel.throw",
					wheel.getThrows(), PottersWheelBlockEntity.THROWS_PER_PIECE));
			return;
		}

		ItemStack piece = new ItemStack(shape.item());
		ItemEntity drop = new ItemEntity(level, x, pos.getY() + HEAD_TOP + 0.6, z, piece, 0.0, 0.1, 0.0);
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
		if (player instanceof ServerPlayer serverPlayer) {
			FireclayCriteria.POTTER_ACTION.trigger(serverPlayer, PotterActionTrigger.THREW, piece, false);
		}
	}

	/** Actionbar: "Shaping: Unfired Jar · 4 clay (3 loaded)". */
	private static void sendShaping(Player player, PottersWheelBlockEntity wheel) {
		PottersWheelBlockEntity.Shape shape = wheel.getShape();
		player.sendOverlayMessage(Component.translatable("message.fireclay.wheel.shaping",
				Component.translatable(shape.item().getDescriptionId()), shape.cost(), wheel.getCount()));
	}

	/** The scheduled tick from a throw: the head stops spinning. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(SPINNING)) {
			level.setBlock(pos, state.setValue(SPINNING, false), Block.UPDATE_ALL);
		}
	}

	/** A player's very first wheel gives them The Potter's Primer. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && placer instanceof Player player && !PotterRecord.get(player).primerGiven()) {
			PottersPrimer.giveTo(player);
		}
	}
}
