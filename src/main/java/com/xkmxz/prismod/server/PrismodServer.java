package com.xkmxz.prismod.server;

import com.xkmxz.prismod.command.server.PrismodServerCommands;
import com.xkmxz.prismod.command.test.server.PrismodServerApiCommands;
import com.xkmxz.prismod.network.transport.PolicyNetwork;
import com.xkmxz.prismod.server.application.ServerFilterApplication;
import net.minecraftforge.common.MinecraftForge;

/** Server lifecycle adapter and application assembly point. */
public final class PrismodServer {
    private static boolean initialized;

    private PrismodServer() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        PolicyNetwork.get().initialize();
        ServerFilterApplication.initialize(PolicyNetwork.get());
        MinecraftForge.EVENT_BUS.addListener(PrismodServerCommands::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(PrismodServerApiCommands::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ServerFilterApplication::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ServerFilterApplication::onServerStopped);
        initialized = true;
    }
}
