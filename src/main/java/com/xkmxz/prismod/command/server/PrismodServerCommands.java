package com.xkmxz.prismod.command.server;

import com.mojang.brigadier.CommandDispatcher;
import com.xkmxz.prismod.api.server.FilterServerApi;
import com.xkmxz.prismod.api.server.ServerStatus;
import com.xkmxz.prismod.api.server.ServerFilterStatus;
import com.xkmxz.prismod.api.common.result.OperationResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.literal;

/** Registers Prismod's server command tree. */
public final class PrismodServerCommands {
    public static final int MANAGEMENT_PERMISSION_LEVEL = 2;

    private PrismodServerCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("prismod")
                .executes(context -> sendHelp(context.getSource()))
                .then(literal("help").executes(context -> sendHelp(context.getSource())))
                .then(literal("status").executes(context -> sendStatus(context.getSource())))
                .then(filterCommands()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> filterCommands() {
        return literal("filter").requires(source -> source == null || source.hasPermission(MANAGEMENT_PERMISSION_LEVEL))
                .then(selectCommands())
                .then(overrideCommands())
                .then(clearCommands());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> selectCommands() {
        return literal("select")
                .then(literal("broadcast").then(selectionArguments(false)))
                .then(literal("player").then(Commands.argument("target", EntityArgument.player()).then(selectionArguments(true))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> selectionArguments(boolean player) {
        var id = Commands.argument("id", ResourceLocationArgument.id());
        if (player) {
            id.executes(context -> selectPlayer(context.getSource(), EntityArgument.getPlayer(context, "target"), ResourceLocationArgument.getId(context, "id"), 1.0F));
            id.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .executes(context -> selectPlayer(context.getSource(), EntityArgument.getPlayer(context, "target"), ResourceLocationArgument.getId(context, "id"), FloatArgumentType.getFloat(context, "strength"))));
        } else {
            id.executes(context -> selectBroadcast(context.getSource(), ResourceLocationArgument.getId(context, "id"), 1.0F));
            id.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .executes(context -> selectBroadcast(context.getSource(), ResourceLocationArgument.getId(context, "id"), FloatArgumentType.getFloat(context, "strength"))));
        }
        return id;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> overrideCommands() {
        var broadcastOwner = Commands.argument("owner", StringArgumentType.word());
        var broadcastId = Commands.argument("id", ResourceLocationArgument.id());
        var broadcastPriority = Commands.argument("priority", IntegerArgumentType.integer());
        broadcastPriority.executes(context -> overrideBroadcast(context.getSource(), StringArgumentType.getString(context, "owner"), ResourceLocationArgument.getId(context, "id"), IntegerArgumentType.getInteger(context, "priority"), 1.0F));
        broadcastPriority.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F)).executes(context -> overrideBroadcast(context.getSource(), StringArgumentType.getString(context, "owner"), ResourceLocationArgument.getId(context, "id"), IntegerArgumentType.getInteger(context, "priority"), FloatArgumentType.getFloat(context, "strength"))));
        broadcastId.then(broadcastPriority);
        broadcastOwner.then(broadcastId);

        var player = Commands.argument("target", EntityArgument.player());
        var playerOwner = Commands.argument("owner", StringArgumentType.word());
        var playerId = Commands.argument("id", ResourceLocationArgument.id());
        var playerPriority = Commands.argument("priority", IntegerArgumentType.integer());
        playerPriority.executes(context -> overridePlayer(context.getSource(), EntityArgument.getPlayer(context, "target"), StringArgumentType.getString(context, "owner"), ResourceLocationArgument.getId(context, "id"), IntegerArgumentType.getInteger(context, "priority"), 1.0F));
        playerPriority.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F)).executes(context -> overridePlayer(context.getSource(), EntityArgument.getPlayer(context, "target"), StringArgumentType.getString(context, "owner"), ResourceLocationArgument.getId(context, "id"), IntegerArgumentType.getInteger(context, "priority"), FloatArgumentType.getFloat(context, "strength"))));
        playerId.then(playerPriority);
        playerOwner.then(playerId);
        player.then(playerOwner);

        return literal("override").then(literal("broadcast").then(broadcastOwner)).then(literal("player").then(player));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> clearCommands() {
        return literal("clear")
                .then(literal("selection").executes(context -> report(context.getSource(), FilterServerApi.clearSelection())))
                .then(literal("all").executes(context -> clearAll(context.getSource())))
                .then(literal("broadcast").then(Commands.argument("owner", StringArgumentType.word())
                        .executes(context -> clearBroadcast(context.getSource(), StringArgumentType.getString(context, "owner")))))
                .then(literal("player").then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .executes(context -> clearPlayer(context.getSource(), EntityArgument.getPlayer(context, "target"), StringArgumentType.getString(context, "owner"))))));
    }

    private static int selectBroadcast(CommandSourceStack source, ResourceLocation id, float strength) {
        return report(source, FilterServerApi.broadcastSelection(new FilterSelectionRequest(id, strength)));
    }

    private static int selectPlayer(CommandSourceStack source, net.minecraft.server.level.ServerPlayer player, ResourceLocation id, float strength) {
        return report(source, FilterServerApi.sendSelection(player, new FilterSelectionRequest(id, strength)));
    }

    private static int overrideBroadcast(CommandSourceStack source, String owner, ResourceLocation id, int priority, float strength) {
        return report(source, FilterServerApi.broadcastOverride(new FilterOverrideRequest(owner, id, strength, priority)));
    }

    private static int overridePlayer(CommandSourceStack source, net.minecraft.server.level.ServerPlayer player, String owner, ResourceLocation id, int priority, float strength) {
        return report(source, FilterServerApi.sendOverride(player, new FilterOverrideRequest(owner, id, strength, priority)));
    }

    private static int clearBroadcast(CommandSourceStack source, String owner) {
        return report(source, FilterServerApi.clearBroadcastOverride(owner));
    }

    private static int clearPlayer(CommandSourceStack source, net.minecraft.server.level.ServerPlayer player, String owner) {
        return report(source, FilterServerApi.clearPlayerOverride(player, owner));
    }

    private static int clearAll(CommandSourceStack source) {
        return report(source, FilterServerApi.clearAllOverrides());
    }

    private static int report(CommandSourceStack source, OperationResult result) {
        Component message = Component.translatable(result.accepted()
                        ? "command.prismod.operation.success"
                        : "command.prismod.operation.failure",
                result.code().name().toLowerCase(), result.target(), result.affectedCount());
        if (result.accepted()) source.sendSuccess(() -> message, false);
        else source.sendFailure(message);
        return result.accepted() ? 1 : 0;
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.prismod.help"), false);
        return 1;
    }

    private static int sendStatus(CommandSourceStack source) {
        ServerStatus status = FilterServerApi.status();
        ServerFilterStatus filters = FilterServerApi.filterStatus();
        source.sendSuccess(() -> Component.translatable(
                "command.prismod.status",
                status.initialized(),
                status.protocolVersion(),
                status.networkChannel()), false);
        source.sendSuccess(() -> Component.translatable("command.prismod.filters",
                filters.globalSelection() == null ? "none" : filters.globalSelection(),
                filters.globalStrength(), filters.globalOverrideCount(), filters.playerSelectionCount(),
                filters.playerOverrideCount(), filters.generation()), false);
        return 1;
    }
}
