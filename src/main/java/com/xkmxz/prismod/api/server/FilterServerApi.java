package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.server.application.ServerFilterApplication;

import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import com.xkmxz.prismod.api.common.result.OperationResult;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import net.minecraft.server.level.ServerPlayer;

/** Internal server-facing API for Prismod's future server features. */
public final class FilterServerApi {
    public static final ResourceLocation NETWORK_CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(
            PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH);
    public static final String PROTOCOL_VERSION = PolicyTransport.PROTOCOL_VERSION;

    private FilterServerApi() {
    }

    public static ServerStatus status() {
        return ServerFilterApplication.status();
    }

    public static ServerFilterStatus filterStatus() {
        return ServerFilterApplication.filterStatus();
    }

    public static OperationResult broadcastSelection(FilterSelectionRequest request) { return ServerFilterApplication.broadcastSelection(request); }
    public static OperationResult sendSelection(ServerPlayer player, FilterSelectionRequest request) { return ServerFilterApplication.sendSelection(player, request); }
    public static OperationResult broadcastOverride(FilterOverrideRequest request) { return ServerFilterApplication.broadcastOverride(request); }
    public static OperationResult sendOverride(ServerPlayer player, FilterOverrideRequest request) { return ServerFilterApplication.sendOverride(player, request); }
    public static OperationResult clearBroadcastOverride(String ownerId) { return ServerFilterApplication.clearBroadcastOverride(ownerId); }
    public static OperationResult clearPlayerOverride(ServerPlayer player, String ownerId) { return ServerFilterApplication.clearPlayerOverride(player, ownerId); }
    public static OperationResult clearAllOverrides() { return ServerFilterApplication.clearAllOverrides(); }
    public static OperationResult clearSelection() { return ServerFilterApplication.clearSelection(); }
}
