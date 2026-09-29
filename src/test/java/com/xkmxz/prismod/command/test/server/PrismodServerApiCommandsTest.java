package com.xkmxz.prismod.command.test.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismodServerApiCommandsTest {
    @Test
    void registersServerApiTestCommandTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PrismodServerApiCommands.register(dispatcher);

        assertEquals(0, dispatcher.parse("prismod_server api help", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api status", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api select broadcast prismod:original", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api select player", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api override broadcast prismod:original 100", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api override player", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api clear selection", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api clear owner", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api clear all", null).getExceptions().size());
        assertEquals(0, dispatcher.parse("prismod_server api reset", null).getExceptions().size());
    }
}
