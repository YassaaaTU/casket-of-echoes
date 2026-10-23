package com.soulsvault.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /casket attune <targets> [attuned]} (permission level 2): attunes players to their Casket, e.g. as an FTB
 * Quests command reward, or seals it again with {@code false}.
 */
public final class AttunementCommands {

    private AttunementCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("casket")
                .then(Commands.literal("attune")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> attune(ctx, true))
                                .then(Commands.argument("attuned", BoolArgumentType.bool())
                                        .executes(ctx -> attune(ctx, BoolArgumentType.getBool(ctx, "attuned")))))));
    }

    private static int attune(CommandContext<CommandSourceStack> ctx, boolean attuned) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;
        for (ServerPlayer target : targets) {
            if (Attunement.setAttuned(target, attuned, true)) {
                changed++;
            }
        }
        int count = changed;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                attuned ? "commands.soulsvault.attune.success" : "commands.soulsvault.attune.revoked", count), true);
        return count;
    }
}
