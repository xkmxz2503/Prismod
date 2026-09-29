package com.xkmxz.prismod.command.test.server;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.CommandDispatcher;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.common.result.OperationResult;
import com.xkmxz.prismod.api.server.FilterServerApi;
import com.xkmxz.prismod.api.server.ServerFilterStatus;
import com.xkmxz.prismod.api.server.ServerStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.literal;

/** Server-only commands for manually exercising the public FilterServerApi. */
public final class PrismodServerApiCommands {
    public static final int TEST_PERMISSION_LEVEL = 2;
    public static final String TEST_OWNER = "prismod-server-api-test";

    private PrismodServerApiCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("prismod_server")
                .requires(source -> source == null || source.hasPermission(TEST_PERMISSION_LEVEL))
                .then(literal("api")
                        .then(literal("help").executes(context -> help(context.getSource())))
                        .then(literal("status").executes(context -> status(context.getSource())))
                        .then(selectCommands())
                        .then(overrideCommands())
                        .then(clearCommands())
                        .then(literal("reset").executes(context -> reset(context.getSource())))));
    }

    private static int help(CommandSourceStack source) {
        message(source, "command.prismod.server_test.help.title");
        message(source, "command.prismod.server_test.help.status");
        message(source, "command.prismod.server_test.help.select_broadcast");
        message(source, "command.prismod.server_test.help.select_player");
        message(source, "command.prismod.server_test.help.override_broadcast");
        message(source, "command.prismod.server_test.help.override_player");
        message(source, "command.prismod.server_test.help.clear_selection");
        message(source, "command.prismod.server_test.help.clear_owner");
        message(source, "command.prismod.server_test.help.clear_all");
        message(source, "command.prismod.server_test.help.reset");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> selectCommands() {
        return literal("select")
                .then(literal("broadcast").then(selectionArguments(false)))
                .then(literal("player").then(Commands.argument("target", EntityArgument.player())
                        .then(selectionArguments(true))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> selectionArguments(boolean player) {
        var id = Commands.argument("filter", ResourceLocationArgument.id());
        if (player) {
            id.executes(context -> selectPlayer(context,
                    EntityArgument.getPlayer(context, "target"),
                    ResourceLocationArgument.getId(context, "filter"), 1.0F));
            id.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .executes(context -> selectPlayer(context,
                            EntityArgument.getPlayer(context, "target"),
                            ResourceLocationArgument.getId(context, "filter"),
                            FloatArgumentType.getFloat(context, "strength"))));
        } else {
            id.executes(context -> selectBroadcast(context,
                    ResourceLocationArgument.getId(context, "filter"), 1.0F));
            id.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .executes(context -> selectBroadcast(context,
                            ResourceLocationArgument.getId(context, "filter"),
                            FloatArgumentType.getFloat(context, "strength"))));
        }
        return id;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> overrideCommands() {
        return literal("override")
                .then(literal("broadcast").then(overrideArguments(false)))
                .then(literal("player").then(Commands.argument("target", EntityArgument.player())
                        .then(overrideArguments(true))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> overrideArguments(boolean player) {
        var id = Commands.argument("filter", ResourceLocationArgument.id());
        var priority = Commands.argument("priority", IntegerArgumentType.integer());
        if (player) {
            priority.executes(context -> overridePlayer(context,
                    EntityArgument.getPlayer(context, "target"),
                    ResourceLocationArgument.getId(context, "filter"),
                    IntegerArgumentType.getInteger(context, "priority"), 1.0F));
            priority.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .executes(context -> overridePlayer(context,
                            EntityArgument.getPlayer(context, "target"),
                            ResourceLocationArgument.getId(context, "filter"),
                            IntegerArgumentType.getInteger(context, "priority"),
                            FloatArgumentType.getFloat(context, "strength"))));
        } else {
            priority.executes(context -> overrideBroadcast(context,
                    ResourceLocationArgument.getId(context, "filter"),
                    IntegerArgumentType.getInteger(context, "priority"), 1.0F));
            priority.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                    .executes(context -> overrideBroadcast(context,
                            ResourceLocationArgument.getId(context, "filter"),
                            IntegerArgumentType.getInteger(context, "priority"),
                            FloatArgumentType.getFloat(context, "strength"))));
        }
        id.then(priority);
        return id;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearCommands() {
        return literal("clear")
                .then(literal("selection").executes(context -> report(context.getSource(),
                        FilterServerApi.clearSelection())))
                .then(literal("owner").executes(context -> report(context.getSource(),
                        FilterServerApi.clearBroadcastOverride(TEST_OWNER))))
                .then(literal("all").executes(context -> report(context.getSource(),
                        FilterServerApi.clearAllOverrides())));
    }

    private static int selectBroadcast(CommandContext<CommandSourceStack> context,
                                       ResourceLocation filter, float strength) {
        return report(context.getSource(), FilterServerApi.broadcastSelection(
                new FilterSelectionRequest(filter, strength)));
    }

    private static int selectPlayer(CommandContext<CommandSourceStack> context,
                                    ServerPlayer player, ResourceLocation filter, float strength) {
        return report(context.getSource(), FilterServerApi.sendSelection(player,
                new FilterSelectionRequest(filter, strength)));
    }

    private static int overrideBroadcast(CommandContext<CommandSourceStack> context,
                                         ResourceLocation filter, int priority, float strength) {
        return report(context.getSource(), FilterServerApi.broadcastOverride(
                new FilterOverrideRequest(TEST_OWNER, filter, strength, priority)));
    }

    private static int overridePlayer(CommandContext<CommandSourceStack> context,
                                      ServerPlayer player, ResourceLocation filter,
                                      int priority, float strength) {
        return report(context.getSource(), FilterServerApi.sendOverride(player,
                new FilterOverrideRequest(TEST_OWNER, filter, strength, priority)));
    }

    private static int status(CommandSourceStack source) {
        ServerStatus server = FilterServerApi.status();
        ServerFilterStatus filters = FilterServerApi.filterStatus();
        message(source, "command.prismod.server_test.status.server", server.initialized(),
                server.protocolVersion(), server.networkChannel());
        Object globalSelection = filters.globalSelection() == null
                ? Component.translatable("command.prismod.server_test.none")
                : filters.globalSelection();
        message(source, "command.prismod.server_test.status.filters",
                globalSelection,
                filters.globalStrength(), filters.globalOverrideCount(),
                filters.playerSelectionCount(), filters.playerOverrideCount(), filters.generation());
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        message(source, "command.prismod.server_test.reset.begin");
        int selection = report(source, FilterServerApi.clearSelection());
        int overrides = report(source, FilterServerApi.clearAllOverrides());
        message(source, "command.prismod.server_test.reset.done", selection == 1 && overrides == 1);
        return selection == 1 && overrides == 1 ? 1 : 0;
    }

    private static int report(CommandSourceStack source, OperationResult result) {
        Component message = Component.translatable(result.accepted()
                        ? "command.prismod.server_test.operation.success"
                        : "command.prismod.server_test.operation.failure",
                result.status(), result.code(), result.requestId(), result.target(),
                result.affectedCount(), result.generation(), result.parameters());
        if (result.accepted()) {
            source.sendSuccess(() -> message, false);
        } else {
            source.sendFailure(message);
        }
        return result.accepted() ? 1 : 0;
    }

    private static void message(CommandSourceStack source, String key, Object... arguments) {
        source.sendSuccess(() -> Component.translatable(key, arguments), false);
    }
}
