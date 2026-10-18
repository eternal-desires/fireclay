package io.github.eternaldesires.fireclay.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayItems;

/**
 * Loaded clay, chosen shape and throw count of a potter's wheel (design §3.4). The block drives the interaction and
 * mirrors this state into the {@code clay}/{@code fireclay} blockstate via {@link #withLump(BlockState)}.
 * Owned by the workshop feature.
 */
public class PottersWheelBlockEntity extends BlockEntity {
	/** Most clay balls the wheel holds. */
	public static final int MAX_CLAY = 8;
	/** Throws needed to finish a piece. */
	public static final int THROWS_PER_PIECE = 5;

	/** What the wheel can shape, with the clay each piece costs (design §3.2). */
	public enum Shape {
		TILE(1, false),
		BELL(3, false),
		JAR(4, false),
		URN(6, false),
		EFFIGY(8, true);

		private final int cost;
		private final boolean needsFireclay;

		Shape(int cost, boolean needsFireclay) {
			this.cost = cost;
			this.needsFireclay = needsFireclay;
		}

		public int cost() {
			return cost;
		}

		public boolean needsFireclay() {
			return needsFireclay;
		}

		/** The greenware this shape produces. A method rather than a field so enum init never touches the item registry. */
		public Item item() {
			return switch (this) {
				case TILE -> FireclayItems.UNFIRED_TILE;
				case BELL -> FireclayItems.UNFIRED_BELL;
				case JAR -> FireclayItems.UNFIRED_JAR;
				case URN -> FireclayItems.UNFIRED_URN;
				case EFFIGY -> FireclayItems.UNFIRED_EFFIGY;
			};
		}

		static Shape byIndex(int index) {
			Shape[] values = values();
			return values[Mth.clamp(index, 0, values.length - 1)];
		}
	}

	private int count;
	private boolean fireclay;
	private Shape shape = Shape.TILE;
	private int throwCount;

	public PottersWheelBlockEntity(BlockPos pos, BlockState state) {
		super(FireclayBlockEntities.POTTERS_WHEEL, pos, state);
	}

	/** Loaded clay balls, 0..{@value #MAX_CLAY}. */
	public int getCount() {
		return count;
	}

	/** Whether the loaded clay is fireclay (false when nothing is loaded). */
	public boolean isFireclay() {
		return fireclay;
	}

	public Shape getShape() {
		return shape;
	}

	/** Throws made towards the current piece, 0..4. */
	public int getThrows() {
		return throwCount;
	}

	/** The ball item currently loaded, used for drops and particles. */
	public Item loadedBall() {
		return fireclay ? FireclayItems.FIRECLAY_BALL : Items.CLAY_BALL;
	}

	/** Adds one ball. The caller has already checked {@link #MAX_CLAY} and the no-mixing rule. */
	void addClay(boolean isFireclay) {
		if (count == 0) {
			fireclay = isFireclay;
		}
		count++;
		setChanged();
	}

	/**
	 * Picks the next shape in order. The Effigy is offered only for fireclay (or an empty wheel, so the potter can
	 * choose it before loading). Resets the throw count.
	 */
	void cycleShape() {
		Shape next = shape;
		do {
			next = Shape.byIndex((next.ordinal() + 1) % Shape.values().length);
		} while (next.needsFireclay() && count > 0 && !fireclay);
		shape = next;
		throwCount = 0;
		setChanged();
	}

	/**
	 * Counts one throw. Returns true when it was the last throw of a piece; the clay for the piece is then used up
	 * and the throw count starts over.
	 */
	boolean addThrow() {
		throwCount++;
		if (throwCount < THROWS_PER_PIECE) {
			setChanged();
			return false;
		}
		throwCount = 0;
		count = Math.max(0, count - shape.cost());
		if (count == 0) {
			fireclay = false;
		}
		setChanged();
		return true;
	}

	/** The blockstate that shows the current lump: {@code clay = ceil(count / 2)} and the clay kind. */
	BlockState withLump(BlockState state) {
		return state.setValue(PottersWheelBlock.CLAY, (count + 1) / 2)
				.setValue(PottersWheelBlock.FIRECLAY, fireclay && count > 0);
	}

	/**
	 * Breaking the wheel (by any means) gives the loaded balls back, like container contents. The wheel itself drops
	 * from its loot table.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null && !level.isClientSide() && count > 0) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, new ItemStack(loadedBall(), count));
			count = 0;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("count", count);
		output.putBoolean("fireclay", fireclay);
		output.putInt("shape", shape.ordinal());
		output.putInt("throws", throwCount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		count = Mth.clamp(input.getIntOr("count", 0), 0, MAX_CLAY);
		fireclay = count > 0 && input.getBooleanOr("fireclay", false);
		shape = Shape.byIndex(input.getIntOr("shape", 0));
		throwCount = Mth.clamp(input.getIntOr("throws", 0), 0, THROWS_PER_PIECE - 1);
	}
}
