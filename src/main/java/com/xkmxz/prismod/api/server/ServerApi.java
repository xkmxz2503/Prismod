package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.server.PrismodServer;
import com.xkmxz.prismod.server.network.PrismodNetwork;
import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import net.minecraft.server.level.ServerPlayer;

/** Internal server-facing API for Prismod's future server features. */
public final class ServerApi {
    public static final ResourceLocation NETWORK_CHANNEL_ID = PrismodNetwork.CHANNEL_ID;
    public static final String PROTOCOL_VERSION = PrismodNetwork.PROTOCOL_VERSION;

    private ServerApi() {
    }

    public static ServerStatus status() {
        return PrismodServer.status();
    }

    public static ServerOperationResult broadcastSelection(FilterSelectionRequest request) { return PrismodServer.broadcastSelection(request); }
    public static ServerOperationResult sendSelection(ServerPlayer player, FilterSelectionRequest request) { return PrismodServer.sendSelection(player, request); }
    public static ServerOperationResult broadcastOverride(FilterOverrideRequest request) { return PrismodServer.broadcastOverride(request); }
    public static ServerOperationResult sendOverride(ServerPlayer player, FilterOverrideRequest request) { return PrismodServer.sendOverride(player, request); }
    public static ServerOperationResult clearBroadcastOverride(String ownerId) { return PrismodServer.clearBroadcastOverride(ownerId); }
    public static ServerOperationResult clearPlayerOverride(ServerPlayer player, String ownerId) { return PrismodServer.clearPlayerOverride(player, ownerId); }
}
