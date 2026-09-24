package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.server.PrismodServer;
import com.xkmxz.prismod.server.network.PrismodNetwork;
import net.minecraft.resources.ResourceLocation;

/** Internal server-facing API for Prismod's future server features. */
public final class ServerApi {
    public static final ResourceLocation NETWORK_CHANNEL_ID = PrismodNetwork.CHANNEL_ID;
    public static final String PROTOCOL_VERSION = PrismodNetwork.PROTOCOL_VERSION;

    private ServerApi() {
    }

    public static ServerStatus status() {
        return PrismodServer.status();
    }
}
