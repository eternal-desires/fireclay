package io.github.eternaldesires.fireclay.block;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.eternaldesires.fireclay.component.Masterwork;
import io.github.eternaldesires.fireclay.component.ReliquaryCharges;
import io.github.eternaldesires.fireclay.registry.FireclayBlockEntities;
import io.github.eternaldesires.fireclay.registry.FireclayComponents;

/**
 * Owner and remaining charges of a Reliquary Urn (design §3.11). The captured items themselves live in
 * {@link ReliquaryData}; this block entity only mirrors whether its owner's binding is full into {@code filled}.
 * Owned by the ceramics feature.
 */
public class ReliquaryUrnBlockEntity extends BlockEntity {
	public static final int CHARGES = 1;
	public static final int MASTERWORK_CHARGES = 2;
	private static final int SYNC_INTERVAL = 40;

	private @Nullable UUID owner;
	private String ownerName = "";
	/** Remaining passings; null until the urn has been placed from an item without the component. */
	private @Nullable Integer charges;
	private @Nullable Masterwork masterwork;

	public ReliquaryUrnBlockEntity(BlockPos pos, BlockState state) {
		super(FireclayBlockEntities.RELIQUARY_URN, pos, state);
	}

	public @Nullable UUID getOwner() {
		return owner;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public void setOwner(@Nullable UUID owner, String name) {
		this.owner = owner;
		this.ownerName = owner == null ? "" : name;
		setChanged();
	}

	public boolean isMasterwork() {
		return getBlockState().getValue(ReliquaryUrnBlock.MASTERWORK);
	}

	public int getCharges() {
		return charges != null ? charges : isMasterwork() ? MASTERWORK_CHARGES : CHARGES;
	}

	public void setCharges(int charges) {
		this.charges = Math.max(0, charges);
		setChanged();
	}

	public GlobalPos globalPos() {
		return GlobalPos.of(level.dimension(), worldPosition);
	}

	/** Every 40 ticks: drop a stale owner (rebound elsewhere or unbound) and mirror the binding into {@code filled}. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, ReliquaryUrnBlockEntity urn) {
		if (level.getGameTime() % SYNC_INTERVAL == 0 && level instanceof ServerLevel serverLevel) {
			urn.syncWithData(serverLevel);
		}
	}

	public void syncWithData(ServerLevel level) {
		boolean filled = false;
		if (owner != null) {
			ReliquaryData data = ReliquaryData.get(level.getServer());
			GlobalPos here = globalPos();
			if (data.isBoundTo(owner, here)) {
				filled = data.get(owner).map(ReliquaryData.Binding::isFilled).orElse(false);
			} else {
				setOwner(null, "");
			}
		}
		BlockState state = level.getBlockState(worldPosition);
		if (state.hasProperty(ReliquaryUrnBlock.FILLED) && state.getValue(ReliquaryUrnBlock.FILLED) != filled) {
			level.setBlock(worldPosition, state.setValue(ReliquaryUrnBlock.FILLED, filled), Block.UPDATE_ALL);
		}
	}

	/** Any removal: a full urn spills its owner's belongings; a bound urn releases its binding. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (owner == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		ReliquaryData data = ReliquaryData.get(serverLevel.getServer());
		if (data.isBoundTo(owner, GlobalPos.of(serverLevel.dimension(), pos))) {
			data.get(owner).ifPresent(binding -> {
				for (ItemStackWithSlot held : binding.held()) {
					Block.popResource(serverLevel, pos, held.stack().copy());
				}
			});
			data.unbind(owner);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("owner_name", "");
		charges = input.getInt("charges").orElse(null);
		masterwork = input.read("masterwork", Masterwork.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		if (owner != null) {
			output.putString("owner_name", ownerName);
		}
		if (charges != null) {
			output.putInt("charges", charges);
		}
		output.storeNullable("masterwork", Masterwork.CODEC, masterwork);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		ReliquaryCharges stored = components.get(FireclayComponents.RELIQUARY_CHARGES);
		charges = stored != null ? stored.charges() : null;
		masterwork = components.get(FireclayComponents.MASTERWORK);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		int remaining = getCharges();
		if (remaining > 0) {
			components.set(FireclayComponents.RELIQUARY_CHARGES, new ReliquaryCharges(remaining));
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
		output.discard("charges");
		output.discard("masterwork");
	}
}
