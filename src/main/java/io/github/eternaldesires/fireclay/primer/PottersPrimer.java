package io.github.eternaldesires.fireclay.primer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import io.github.eternaldesires.fireclay.attachment.PotterRecord;

/** The Potter's Primer: a vanilla written book with translatable pages (design §3.3). Owned by the workshop feature. */
public final class PottersPrimer {
	public static final String TITLE = "The Potter's Primer";
	public static final String AUTHOR = "The Fireclay Guild";
	public static final int PAGES = 10;

	private PottersPrimer() {
	}

	/** A fresh copy of the book; pages use {@code book.fireclay.primer.p1..p10}. */
	public static ItemStack create() {
		List<Filterable<Component>> pages = new ArrayList<>(PAGES);
		for (int page = 1; page <= PAGES; page++) {
			pages.add(Filterable.passThrough(Component.translatable("book.fireclay.primer.p" + page)));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), AUTHOR, 0, pages, true));
		return book;
	}

	/** Gives a copy (dropping it if the inventory is full) and marks the player's record as primed. Server side. */
	public static void giveTo(Player player) {
		player.getInventory().placeItemBackInInventory(create(), Prediction.SERVER_ONLY);
		PotterRecord.markPrimerGiven(player);
	}
}
