package io.github.eternaldesires.fireclay.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.eternaldesires.fireclay.Fireclay;
import io.github.eternaldesires.fireclay.attachment.PotterRecord;

/** Data attachments (contract §6). */
public final class FireclayAttachments {
	/** On players: persistent, survives death. Prefer the helpers on {@link PotterRecord}. */
	public static final AttachmentType<PotterRecord> POTTER_RECORD = AttachmentRegistry.create(Fireclay.id("potter_record"),
			builder -> builder.persistent(PotterRecord.CODEC).copyOnDeath().initializer(() -> PotterRecord.EMPTY));

	/** On item entities resting on an Ember Tile: transient bake progress in ticks. */
	public static final AttachmentType<Integer> BAKE_PROGRESS = AttachmentRegistry.create(Fireclay.id("bake_progress"));

	private FireclayAttachments() {
	}

	public static void init() {
	}
}
