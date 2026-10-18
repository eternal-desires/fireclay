package io.github.eternaldesires.fireclay.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import io.github.eternaldesires.fireclay.attachment.PotterRecord;
import io.github.eternaldesires.fireclay.kiln.KilnBlockEntity;
import io.github.eternaldesires.fireclay.primer.PottersPrimer;

/**
 * {@code /fireclay kiln <pos> info|heat <value>}, {@code /fireclay primer [targets]}, {@code /fireclay record [player]}
 * (design §3.16). Owned by the systems feature.
 */
public final class FireclayCommand {
	private static final DynamicCommandExceptionType NOT_A_KILN = new DynamicCommandExceptionType(
			pos -> Component.translatable("command.fireclay.not_a_kiln", pos));

	private FireclayCommand() {
	}

	/** Matches {@code CommandRegistrationCallback#register}. */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("fireclay")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
				.then(Commands.literal("kiln")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.then(Commands.literal("info")
										.executes(FireclayCommand::kilnInfo))
								.then(Commands.literal("heat")
										.then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
												.executes(FireclayCommand::kilnHeat)))))
				.then(Commands.literal("primer")
						.executes(context -> givePrimer(context.getSource(), List.of(context.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> givePrimer(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
				.then(Commands.literal("record")
						.executes(context -> showRecord(context.getSource(), context.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> showRecord(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
	}

	private static KilnBlockEntity kilnAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
		if (context.getSource().getLevel().getBlockEntity(pos) instanceof KilnBlockEntity kiln) {
			return kiln;
		}
		throw NOT_A_KILN.create(pos.toShortString());
	}

	private static int kilnInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KilnBlockEntity kiln = kilnAt(context);
		CommandSourceStack source = context.getSource();
		String pos = kiln.getBlockPos().toShortString();
		int heat = (int) Math.round(kiln.getHeat());
		Component damper = Component.translatable(kiln.isDamperOpen()
				? "command.fireclay.kiln.info.damper_open"
				: "command.fireclay.kiln.info.damper_closed");
		Component lining = Component.translatable(kiln.isLined()
				? "command.fireclay.kiln.info.lined"
				: "command.fireclay.kiln.info.unlined");

		source.sendSuccess(() -> Component.translatable("command.fireclay.kiln.info",
				pos, heat, kiln.getCap(), lining, kiln.getBurn(), kiln.getBurnMax(), damper, kiln.getComparatorSignal()), false);
		if (kiln.hasRecipe()) {
			int total = kiln.getTotal();
			int sweetPercent = total > 0 ? kiln.getSweet() * 100 / total : 0;
			source.sendSuccess(() -> Component.translatable("command.fireclay.kiln.info.recipe",
					kiln.getWindowMin(), kiln.getWindowMax(), kiln.getProgress(), total, sweetPercent, kiln.getOver()), false);
		} else {
			source.sendSuccess(() -> Component.translatable("command.fireclay.kiln.info.no_recipe"), false);
		}
		return heat;
	}

	private static int kilnHeat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		KilnBlockEntity kiln = kilnAt(context);
		kiln.setHeat(IntegerArgumentType.getInteger(context, "value"));
		// setHeat clamps to [ambient, cap]; report what the kiln actually holds.
		int heat = (int) Math.round(kiln.getHeat());
		String pos = kiln.getBlockPos().toShortString();
		context.getSource().sendSuccess(() -> Component.translatable("command.fireclay.kiln.heat", pos, heat), true);
		return heat;
	}

	private static int givePrimer(CommandSourceStack source, Collection<ServerPlayer> targets) {
		for (ServerPlayer player : targets) {
			PottersPrimer.giveTo(player);
		}
		if (targets.size() == 1) {
			Component name = targets.iterator().next().getDisplayName();
			source.sendSuccess(() -> Component.translatable("command.fireclay.primer.single", name), true);
		} else {
			int count = targets.size();
			source.sendSuccess(() -> Component.translatable("command.fireclay.primer.multiple", count), true);
		}
		return targets.size();
	}

	private static int showRecord(CommandSourceStack source, ServerPlayer player) {
		PotterRecord record = PotterRecord.get(player);
		Component name = player.getDisplayName();
		Component primer = Component.translatable(record.primerGiven()
				? "command.fireclay.record.primer_yes"
				: "command.fireclay.record.primer_no");
		source.sendSuccess(() -> Component.translatable("command.fireclay.record",
				name, record.firings(), record.masterworks(), record.cracks(), primer), false);
		return record.firings();
	}
}
