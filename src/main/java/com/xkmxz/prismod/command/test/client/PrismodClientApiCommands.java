package com.xkmxz.prismod.command.test.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.api.client.contract.FilterSnapshot;
import com.xkmxz.prismod.api.client.contract.FilterSubscription;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/** Client-only commands that simulate another mod consuming Prismod's public API. */
public final class PrismodClientApiCommands {
    private static final String OWNER = "prismod-api-test-mod";
    private static FilterOverride testOverride;
    private static FilterSubscription subscription;

    private PrismodClientApiCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("prismod_client")
                .then(Commands.literal("api")
                        .then(Commands.literal("help").executes(context -> help()))
                        .then(Commands.literal("list").executes(context -> list()))
                        .then(Commands.literal("snapshot").executes(context -> snapshot()))
                        .then(Commands.literal("force")
                                .then(Commands.argument("filter", ResourceLocationArgument.id())
                                        .executes(context -> force(context, 1.0F))
                                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(context -> force(context,
                                                        FloatArgumentType.getFloat(context, "strength"))))))
                        .then(Commands.literal("clear").executes(context -> clear()))
                        .then(Commands.literal("watch").executes(context -> watch()))
                        .then(Commands.literal("watch_events").executes(context -> watchEvents()))
                        .then(Commands.literal("owner_clear").executes(context -> ownerClear()))));
    }

    private static int help() {
        message("command.prismod.client.help.title");
        message("command.prismod.client.help.list");
        message("command.prismod.client.help.snapshot");
        message("command.prismod.client.help.force");
        message("command.prismod.client.help.clear");
        message("command.prismod.client.help.watch");
        message("command.prismod.client.help.watch_events");
        message("command.prismod.client.help.owner_clear");
        return 1;
    }

    private static int list() {
        var filters = FilterClientApi.availableFilters();
        message("command.prismod.client.list", filters.size());
        for (FilterDescriptor filter : filters) {
            message("command.prismod.client.list.entry", filter.id(),
                    filter.available() ? "command.prismod.client.available" : "command.prismod.client.unavailable",
                    filter.ownerId(), filter.defaultStrength());
        }
        return filters.size();
    }

    private static int snapshot() {
        FilterSnapshot snapshot = FilterClientApi.snapshot();
        message("command.prismod.client.snapshot", snapshot.filter(), snapshot.strength(), snapshot.forced(),
                snapshot.renderAvailable(), snapshot.fallbackReason(), snapshot.generation());
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> context, float strength) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "filter");
        if (testOverride != null) testOverride.close();
        testOverride = FilterClientApi.createOverride(OWNER, id, strength, 100);
        message("command.prismod.client.force", id, strength);
        return 1;
    }

    private static int clear() {
        if (testOverride == null) {
            message("command.prismod.client.clear.none");
            return 0;
        }
        testOverride.close();
        testOverride = null;
        message("command.prismod.client.clear.done");
        return 1;
    }

    private static int watch() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
            message("command.prismod.client.watch.closed");
            return 1;
        }
        subscription = FilterClientApi.subscribe(snapshot -> message("command.prismod.client.watch.event",
                snapshot.filter(), snapshot.strength(), snapshot.fallbackReason()));
        message("command.prismod.client.watch.opened");
        return 1;
    }

    private static int ownerClear() {
        FilterOperation operation = FilterClientApi.clearOverrides(OWNER);
        testOverride = null;
        message("command.prismod.client.owner_clear", operation.status(), operation.affectedCount());
        return operation.affectedCount();
    }

    private static int watchEvents() {
        if (subscription != null) subscription.close();
        subscription = FilterClientApi.subscribeEvents(event -> message("command.prismod.client.event",
                event.type(), event.filters().size(), event.operation() == null ? "none" : event.operation().status()));
        message("command.prismod.client.events.opened");
        return 1;
    }

    private static void message(String key, Object... arguments) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, arguments), false);
        }
    }
}
