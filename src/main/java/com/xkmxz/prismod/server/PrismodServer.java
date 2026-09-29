package com.xkmxz.prismod.server;

import com.xkmxz.prismod.api.server.ServerStatus;
import com.xkmxz.prismod.command.server.PrismodServerCommands;
import com.xkmxz.prismod.server.network.PrismodNetwork;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.common.state.FilterOperationStatus;
import com.xkmxz.prismod.api.server.ServerOperationResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Common/server bootstrap. This class must remain free of client-only dependencies. */
public final class PrismodServer {
    private static boolean initialized;
    private static long generation;
    private static UUID broadcastSelectionId;
    private static FilterSelectionRequest broadcastSelection;
    private static final Map<UUID, FilterSelectionRequest> playerSelections = new HashMap<>();
    private static final Map<String, UUID> broadcastOverrides = new HashMap<>();
    private static final Map<UUID, Map<String, UUID>> playerOverrides = new HashMap<>();
    private static final Map<String, FilterOverrideRequest> broadcastOverrideRequests = new HashMap<>();
    private static final Map<UUID, Map<String, FilterOverrideRequest>> playerOverrideRequests = new HashMap<>();

    private PrismodServer() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        PrismodNetwork.initialize();
        MinecraftForge.EVENT_BUS.addListener(PrismodServerCommands::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(PrismodServer::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(PrismodServer::onServerStopped);
        initialized = true;
    }

    public static ServerStatus status() {
        return new ServerStatus(initialized, PrismodNetwork.CHANNEL_ID, PrismodNetwork.PROTOCOL_VERSION);
    }

    public static synchronized com.xkmxz.prismod.api.server.ServerFilterStatus filterStatus() {
        int playerOverrideCount = playerOverrideRequests.values().stream().mapToInt(Map::size).sum();
        return new com.xkmxz.prismod.api.server.ServerFilterStatus(
                broadcastSelection == null ? null : broadcastSelection.filter(),
                broadcastSelection == null ? 0.0F : broadcastSelection.strength(),
                broadcastOverrideRequests.size(), playerSelections.size(), playerOverrideCount, generation);
    }

    public static synchronized ServerOperationResult broadcastSelection(FilterSelectionRequest request) {
        if (request == null || request.filter() == null) return invalid("broadcast", 0.0F);
        UUID id = broadcastSelectionId == null ? UUID.randomUUID() : broadcastSelectionId;
        broadcastSelectionId = id;
        broadcastSelection = request;
        var packet = com.xkmxz.prismod.server.network.FilterCommandPacket.selection(id, request.filter(), request.strength());
        sendAll(packet);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                FilterSelectionRequest personal = playerSelections.get(player.getUUID());
                if (personal != null) sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.selection(
                        UUID.randomUUID(), personal.filter(), personal.strength()));
            }
        }
        generation++;
        return success(id, "broadcast", request.strength(), "selection dispatched");
    }

    public static synchronized ServerOperationResult sendSelection(ServerPlayer player, FilterSelectionRequest request) {
        if (player == null || request == null || request.filter() == null) return invalid("player", 0.0F);
        UUID id = UUID.randomUUID();
        playerSelections.put(player.getUUID(), request);
        sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.selection(id, request.filter(), request.strength()));
        generation++;
        return success(id, player.getUUID().toString(), request.strength(), "selection dispatched");
    }

    public static synchronized ServerOperationResult broadcastOverride(FilterOverrideRequest request) {
        if (request == null || request.filter() == null || request.ownerId().isBlank()) return invalid("broadcast", 0.0F);
        UUID id = broadcastOverrides.computeIfAbsent(request.ownerId(), ignored -> UUID.randomUUID());
        broadcastOverrides.put(request.ownerId(), id);
        broadcastOverrideRequests.put(request.ownerId(), request);
        sendAll(com.xkmxz.prismod.server.network.FilterCommandPacket.override(id, request.ownerId(), request.filter(), request.strength(), request.priority()));
        generation++;
        return success(id, "broadcast", request.strength(), "override dispatched");
    }

    public static synchronized ServerOperationResult sendOverride(ServerPlayer player, FilterOverrideRequest request) {
        if (player == null || request == null || request.filter() == null || request.ownerId().isBlank()) return invalid("player", 0.0F);
        Map<String, UUID> overrides = playerOverrides.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        UUID id = overrides.computeIfAbsent(request.ownerId(), ignored -> UUID.randomUUID());
        playerOverrideRequests.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(request.ownerId(), request);
        sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.override(id, request.ownerId(), request.filter(), request.strength(), request.priority()));
        generation++;
        return success(id, player.getUUID().toString(), request.strength(), "override dispatched");
    }

    public static synchronized ServerOperationResult clearBroadcastOverride(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return invalid("broadcast", 0.0F);
        UUID id = broadcastOverrides.remove(ownerId);
        broadcastOverrideRequests.remove(ownerId);
        if (id == null) return new ServerOperationResult(FilterOperationStatus.NOT_FOUND, null, "broadcast", "override not found", 0.0F, generation);
        sendAll(com.xkmxz.prismod.server.network.FilterCommandPacket.clearOverride(id, ownerId));
        generation++;
        return success(id, "broadcast", 0.0F, "override cleared");
    }

    public static synchronized ServerOperationResult clearPlayerOverride(ServerPlayer player, String ownerId) {
        if (player == null || ownerId == null || ownerId.isBlank()) return invalid("player", 0.0F);
        Map<String, UUID> overrides = playerOverrides.get(player.getUUID());
        UUID id = overrides == null ? null : overrides.remove(ownerId);
        Map<String, FilterOverrideRequest> requests = playerOverrideRequests.get(player.getUUID());
        if (requests != null) requests.remove(ownerId);
        if (id == null) return new ServerOperationResult(FilterOperationStatus.NOT_FOUND, null, player.getUUID().toString(), "override not found", 0.0F, generation);
        sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.clearOverride(id, ownerId));
        generation++;
        return success(id, player.getUUID().toString(), 0.0F, "override cleared");
    }

    public static synchronized ServerOperationResult clearAllOverrides() {
        int count = broadcastOverrides.size() + playerOverrideRequests.values().stream().mapToInt(Map::size).sum();
        if (count == 0) {
            return new ServerOperationResult(FilterOperationStatus.NOT_FOUND, null, "all", "no server overrides", 0.0F, generation);
        }
        sendAll(com.xkmxz.prismod.server.network.FilterCommandPacket.clearAllOverrides(UUID.randomUUID()));
        broadcastOverrides.clear();
        broadcastOverrideRequests.clear();
        playerOverrides.clear();
        playerOverrideRequests.clear();
        generation++;
        return new ServerOperationResult(FilterOperationStatus.SUCCESS, null, "all", "cleared " + count + " server overrides", 0.0F, generation);
    }

    private static ServerOperationResult success(UUID id, String target, float strength, String detail) {
        return new ServerOperationResult(FilterOperationStatus.SUCCESS, id, target, detail, strength, generation);
    }

    private static ServerOperationResult invalid(String target, float strength) {
        return new ServerOperationResult(FilterOperationStatus.INVALID_ARGUMENT, null, target, "invalid request", strength, generation);
    }

    private static void sendAll(com.xkmxz.prismod.server.network.FilterCommandPacket packet) {
        try {
            PrismodNetwork.channel().send(PacketDistributor.ALL.noArg(), packet);
        } catch (RuntimeException ignored) {
            // Unit-test and optional server environments may not have a network event bus.
        }
    }

    private static void sendPlayer(ServerPlayer player, com.xkmxz.prismod.server.network.FilterCommandPacket packet) {
        try {
            PrismodNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player), packet);
        } catch (RuntimeException ignored) {
            // Unit-test and optional server environments may not have a network event bus.
        }
    }

    private static synchronized void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (broadcastSelection != null && broadcastSelectionId != null) {
            sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.selection(
                    broadcastSelectionId, broadcastSelection.filter(), broadcastSelection.strength()));
        }
        broadcastOverrideRequests.forEach((owner, request) -> {
            UUID id = broadcastOverrides.get(owner);
            if (id != null) sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.override(
                    id, owner, request.filter(), request.strength(), request.priority()));
        });
        FilterSelectionRequest selection = playerSelections.get(player.getUUID());
        if (selection != null) {
            sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.selection(
                    UUID.randomUUID(), selection.filter(), selection.strength()));
        }
        Map<String, UUID> overrides = playerOverrides.get(player.getUUID());
        Map<String, FilterOverrideRequest> requests = playerOverrideRequests.get(player.getUUID());
        if (overrides != null && requests != null) {
            overrides.forEach((owner, id) -> {
                FilterOverrideRequest request = requests.get(owner);
                if (request != null) sendPlayer(player, com.xkmxz.prismod.server.network.FilterCommandPacket.override(
                        id, owner, request.filter(), request.strength(), request.priority()));
            });
        }
    }

    private static synchronized void onServerStopped(ServerStoppedEvent event) {
        generation = 0L;
        broadcastSelectionId = null;
        broadcastSelection = null;
        broadcastOverrides.clear();
        playerOverrides.clear();
        broadcastOverrideRequests.clear();
        playerOverrideRequests.clear();
        playerSelections.clear();
    }
}
