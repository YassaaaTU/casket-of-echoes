package com.soulsvault.party;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.soulsvault.network.PacketS2COpenPartyScreen;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /casket party ...}: the built-in party system from chat, for keyboard players and server admins.
 * Everything here is also available in the party GUI.
 */
public final class PartyCommands {

    private PartyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("casket")
                .then(Commands.literal("party")
                        .executes(ctx -> openGui(ctx, false))
                        .then(Commands.literal("gui")
                                .executes(ctx -> openGui(ctx, false))
                                .then(Commands.literal("invites").executes(ctx -> openGui(ctx, true))))
                        .then(Commands.literal("create")
                                .executes(ctx -> report(ctx, PartyService.createParty(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> invite(ctx, CasketJoinPolicy.MERGE))
                                        .then(Commands.argument("policy", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(policyNames(), builder))
                                                .executes(ctx -> invite(ctx, parsePolicy(StringArgumentType.getString(ctx, "policy")))))))
                        .then(Commands.literal("accept")
                                .executes(ctx -> accept(ctx, null, false))
                                .then(Commands.argument("policy", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(policyNames(), builder))
                                        .executes(ctx -> accept(ctx, parsePolicy(StringArgumentType.getString(ctx, "policy")), false))
                                        .then(Commands.literal("confirm")
                                                .executes(ctx -> accept(ctx, parsePolicy(StringArgumentType.getString(ctx, "policy")), true)))))
                        .then(Commands.literal("decline").executes(PartyCommands::declineLatest))
                        .then(Commands.literal("leave")
                                .executes(ctx -> report(ctx, PartyService.leave(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("member", StringArgumentType.word())
                                        .executes(ctx -> memberAction(ctx, true))))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("member", StringArgumentType.word())
                                        .executes(ctx -> memberAction(ctx, false))))
                        .then(Commands.literal("disband")
                                .executes(ctx -> report(ctx, PartyService.disband(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("merge")
                                .executes(ctx -> report(ctx, PartyService.mergePrivateCasket(ctx.getSource().getPlayerOrException()))))));
    }

    private static List<String> policyNames() {
        return Arrays.stream(CasketJoinPolicy.values()).map(policy -> policy.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static CasketJoinPolicy parsePolicy(String name) {
        try {
            return CasketJoinPolicy.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CasketJoinPolicy.MERGE;
        }
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx, boolean invitesTab) throws CommandSyntaxException {
        Services.PLATFORM.sendToClient(ctx.getSource().getPlayerOrException(), new PacketS2COpenPartyScreen(invitesTab));
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx, CasketJoinPolicy policy) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return report(ctx, PartyService.invite(ctx.getSource().getPlayerOrException(), target.getGameProfile().getName(), policy));
    }

    private static int accept(CommandContext<CommandSourceStack> ctx, @Nullable CasketJoinPolicy policy, boolean confirmed)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<PartyInvite> invites = PartyService.registry(player.server).getInvites(player.getUUID());
        if (invites.isEmpty()) {
            return report(ctx, Component.translatable("gui.soulsvault.party.error.invite_expired"));
        }
        PartyInvite latest = invites.get(invites.size() - 1);
        return report(ctx, PartyService.accept(player, latest.partyId(), policy != null ? policy : latest.policy(), confirmed));
    }

    private static int declineLatest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<PartyInvite> invites = PartyService.registry(player.server).getInvites(player.getUUID());
        if (!invites.isEmpty()) {
            PartyService.decline(player, invites.get(invites.size() - 1).partyId());
        }
        return 1;
    }

    private static int memberAction(CommandContext<CommandSourceStack> ctx, boolean kick) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "member");
        PartyView party = PartyService.getParty(player.server, player.getUUID());
        UUID target = null;
        if (party != null) {
            for (UUID member : party.members()) {
                if (PartyService.nameOf(player.server, member).equalsIgnoreCase(name)) {
                    target = member;
                }
            }
        }
        if (target == null) {
            return report(ctx, Component.translatable("gui.soulsvault.party.error.not_member", name));
        }
        return report(ctx, kick ? PartyService.kick(player, target) : PartyService.transferLeadership(player, target));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, @Nullable Component error) {
        if (error != null) {
            ctx.getSource().sendFailure(error.copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
}
