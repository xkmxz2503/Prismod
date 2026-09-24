package com.xkmxz.prismod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.xkmxz.prismod.api.server.ServerApi;
import com.xkmxz.prismod.api.server.ServerStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.literal;

/** Registers Prismod's server command tree. */
public final class PrismodCommands {
    public static final int MANAGEMENT_PERMISSION_LEVEL = 2;

    private PrismodCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("prismod")
                .executes(context -> sendHelp(context.getSource()))
                .then(literal("help").executes(context -> sendHelp(context.getSource())))
                .then(literal("status").executes(context -> sendStatus(context.getSource()))));
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.prismod.help"), false);
        return 1;
    }

    private static int sendStatus(CommandSourceStack source) {
        ServerStatus status = ServerApi.status();
        source.sendSuccess(() -> Component.translatable(
                "command.prismod.status",
                status.initialized(),
                status.protocolVersion(),
                status.networkChannel()), false);
        return 1;
    }
}
