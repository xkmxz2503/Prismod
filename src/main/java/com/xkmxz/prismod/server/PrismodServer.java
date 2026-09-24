package com.xkmxz.prismod.server;

import com.xkmxz.prismod.api.server.ServerStatus;
import com.xkmxz.prismod.server.command.PrismodCommands;
import com.xkmxz.prismod.server.network.PrismodNetwork;
import net.minecraftforge.common.MinecraftForge;

/** Common/server bootstrap. This class must remain free of client-only dependencies. */
public final class PrismodServer {
    private static boolean initialized;

    private PrismodServer() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        PrismodNetwork.initialize();
        MinecraftForge.EVENT_BUS.addListener(PrismodCommands::onRegisterCommands);
        initialized = true;
    }

    public static ServerStatus status() {
        return new ServerStatus(initialized, PrismodNetwork.CHANNEL_ID, PrismodNetwork.PROTOCOL_VERSION);
    }
}
