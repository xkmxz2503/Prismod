package com.xkmxz.prismod.command.test.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.submit.result.FilterReceipt;
import com.xkmxz.prismod.api.client.query.request.FilterQuery;
import com.xkmxz.prismod.api.client.submit.request.FilterRequest;
import java.util.concurrent.CompletionStage;
import com.xkmxz.prismod.api.client.query.view.FilterDescriptor;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
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
    private static CompletionStage<FilterReceipt> subscription;

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
                                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 100.0F))
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
        var filters = FilterClientApi.query(new FilterQuery.Filters(true));
        message("command.prismod.client.list", filters.size());
        for (FilterDescriptor filter : filters) {
            message("command.prismod.client.list.entry", filter.id(),
                    filter.available() ? "command.prismod.client.available" : "command.prismod.client.unavailable",
                    filter.ownerId(), filter.defaultStrength());
        }
        return filters.size();
    }

    private static int snapshot() {
        FilterSnapshot snapshot = FilterClientApi.query(new FilterQuery.Snapshot());
        message("command.prismod.client.snapshot", snapshot.filter(), snapshot.strength(), snapshot.forced(),
                snapshot.renderAvailable(), snapshot.fallbackReason(), snapshot.generation());
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> context, float strength) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "filter");
        FilterClientApi.submit(new FilterRequest.ClearOverrides(OWNER))
                .thenCompose(ignored -> FilterClientApi.submit(new FilterRequest.Override(OWNER, id, strength, 100)))
                .thenAccept(receipt -> {
                    if (receipt.status() == FilterOperationStatus.SUCCESS) {
                        message("command.prismod.client.force", id, strength);
                    } else message("command.prismod.client.owner_clear", receipt.status(), receipt.affectedCount());
                });
        return 1;
    }

    private static int clear() {
        FilterClientApi.submit(new FilterRequest.ClearOverrides(OWNER)).thenAccept(receipt -> {
            if (receipt.status() != FilterOperationStatus.SUCCESS || receipt.affectedCount() == 0) {
                message("command.prismod.client.clear.none");
            } else message("command.prismod.client.clear.done");
        });
        return 1;
    }

    private static int watch() {
        if (subscription != null) {
            closeSubscription();
            message("command.prismod.client.watch.closed");
            return 1;
        }
        subscription = FilterClientApi.submit(new FilterRequest.SubscribeState(OWNER,
                snapshot -> message("command.prismod.client.watch.event",
                        snapshot.filter(), snapshot.strength(), snapshot.fallbackReason())));
        message("command.prismod.client.watch.opened");
        return 1;
    }

    private static int ownerClear() {
        FilterClientApi.submit(new FilterRequest.ClearOverrides(OWNER)).thenAccept(receipt ->
                message("command.prismod.client.owner_clear", receipt.status(), receipt.affectedCount()));
        return 1;
    }

    private static int watchEvents() {
        closeSubscription();
        subscription = FilterClientApi.submit(new FilterRequest.SubscribeEvents(OWNER,
                event -> message("command.prismod.client.event", event.type(), event.filters().size(),
                        event.receipt() == null ? "none" : event.receipt().status())));
        message("command.prismod.client.events.opened");
        return 1;
    }

    private static void closeSubscription() {
        if (subscription == null) return;
        subscription.thenAccept(receipt -> receipt.resourceIds().forEach(id ->
                FilterClientApi.submit(new FilterRequest.Release(id))));
        subscription = null;
    }

    private static void message(String key, Object... arguments) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, arguments), false);
        }
    }
}
