package com.xkmxz.prismod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismodCommandsTest {
    @Test
    void registersRootHelpAndStatusCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PrismodCommands.register(dispatcher);

        assertEquals(0, dispatcher.parse("prismod", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod help", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod status", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod filter select broadcast prismod:original", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod filter override broadcast server-rule prismod:original 10", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod filter clear broadcast server-rule", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod filter clear all", null).getExceptions().size());
    }
}
