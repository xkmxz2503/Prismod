package com.xkmxz.prismod.server.application;

import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.result.OperationResult;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.server.ServerFilterStatus;
import com.xkmxz.prismod.api.server.ServerStatus;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared server use-case layer used by both the public API and commands. */
public final class ServerFilterApplication {
    private static boolean initialized;
    private static PolicyTransport transport = new PolicyTransport() {
        @Override public void broadcast(PolicyMessage message) { }
        @Override public void send(ServerPlayer player, PolicyMessage message) { }
        @Override public void registerClientSink(java.util.function.Consumer<PolicyMessage> sink) { }
    };
    private static long generation;
    private static UUID broadcastSelectionId;
    private static FilterSelectionRequest broadcastSelection;
    private static final Map<UUID, FilterSelectionRequest> playerSelections = new HashMap<>();
    private static final Map<String, UUID> broadcastOverrides = new HashMap<>();
    private static final Map<UUID, Map<String, UUID>> playerOverrides = new HashMap<>();
    private static final Map<String, FilterOverrideRequest> broadcastOverrideRequests = new HashMap<>();
    private static final Map<UUID, Map<String, FilterOverrideRequest>> playerOverrideRequests = new HashMap<>();

    private ServerFilterApplication() {
    }

    public static synchronized void initialize(PolicyTransport policyTransport) {
        transport = policyTransport;
        initialized = true;
    }

    public static ServerStatus status() {
        return new ServerStatus(initialized,
                ResourceLocation.fromNamespaceAndPath(PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH),
                PolicyTransport.PROTOCOL_VERSION);
    }

    public static synchronized ServerFilterStatus filterStatus() {
        int playerOverrideCount = playerOverrideRequests.values().stream().mapToInt(Map::size).sum();
        return new ServerFilterStatus(broadcastSelection == null ? null : broadcastSelection.filter(),
                broadcastSelection == null ? 0.0F : broadcastSelection.strength(), broadcastOverrideRequests.size(),
                playerSelections.size(), playerOverrideCount, generation);
    }

    public static synchronized OperationResult broadcastSelection(FilterSelectionRequest request) {
        if (request == null || request.filter() == null) return invalid("broadcast");
        UUID id = broadcastSelectionId == null ? UUID.randomUUID() : broadcastSelectionId;
        broadcastSelectionId = id;
        broadcastSelection = request;
        transport().broadcast(PolicyMessage.selection(id, request.filter(), request.strength()));
        resendPersonalSelections();
        generation++;
        return success(id, "broadcast", 1);
    }

    public static synchronized OperationResult sendSelection(ServerPlayer player, FilterSelectionRequest request) {
        if (player == null || request == null || request.filter() == null) return invalid("player");
        UUID id = UUID.randomUUID();
        playerSelections.put(player.getUUID(), request);
        transport().send(player, PolicyMessage.selection(id, request.filter(), request.strength()));
        generation++;
        return success(id, player.getUUID().toString(), 1);
    }

    public static synchronized OperationResult clearSelection() {
        if (broadcastSelection == null && playerSelections.isEmpty()) return notFound("all");
        transport().broadcast(PolicyMessage.clearSelection(UUID.randomUUID()));
        broadcastSelectionId = null;
        broadcastSelection = null;
        playerSelections.clear();
        generation++;
        return success(null, "all", 1);
    }

    public static synchronized OperationResult broadcastOverride(FilterOverrideRequest request) {
        if (request == null || request.filter() == null || request.ownerId().isBlank()) return invalid("broadcast");
        UUID id = broadcastOverrides.computeIfAbsent(request.ownerId(), ignored -> UUID.randomUUID());
        broadcastOverrideRequests.put(request.ownerId(), request);
        transport().broadcast(PolicyMessage.override(id, request.ownerId(), request.filter(), request.strength(), request.priority()));
        generation++;
        return success(id, "broadcast", 1);
    }

    public static synchronized OperationResult sendOverride(ServerPlayer player, FilterOverrideRequest request) {
        if (player == null || request == null || request.filter() == null || request.ownerId().isBlank()) return invalid("player");
        Map<String, UUID> overrides = playerOverrides.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        UUID id = overrides.computeIfAbsent(request.ownerId(), ignored -> UUID.randomUUID());
        playerOverrideRequests.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(request.ownerId(), request);
        transport().send(player, PolicyMessage.override(id, request.ownerId(), request.filter(), request.strength(), request.priority()));
        generation++;
        return success(id, player.getUUID().toString(), 1);
    }

    public static synchronized OperationResult clearBroadcastOverride(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return invalid("broadcast");
        UUID id = broadcastOverrides.remove(ownerId);
        broadcastOverrideRequests.remove(ownerId);
        if (id == null) return notFound("broadcast");
        transport().broadcast(PolicyMessage.clearOverride(id, ownerId));
        generation++;
        return success(id, "broadcast", 1);
    }

    public static synchronized OperationResult clearPlayerOverride(ServerPlayer player, String ownerId) {
        if (player == null || ownerId == null || ownerId.isBlank()) return invalid("player");
        Map<String, UUID> overrides = playerOverrides.get(player.getUUID());
        UUID id = overrides == null ? null : overrides.remove(ownerId);
        Map<String, FilterOverrideRequest> requests = playerOverrideRequests.get(player.getUUID());
        if (requests != null) requests.remove(ownerId);
        if (id == null) return notFound(player.getUUID().toString());
        transport().send(player, PolicyMessage.clearOverride(id, ownerId));
        generation++;
        return success(id, player.getUUID().toString(), 1);
    }

    public static synchronized OperationResult clearAllOverrides() {
        int count = broadcastOverrides.size() + playerOverrideRequests.values().stream().mapToInt(Map::size).sum();
        if (count == 0) return notFound("all");
        transport().broadcast(PolicyMessage.clearAllOverrides(UUID.randomUUID()));
        broadcastOverrides.clear();
        broadcastOverrideRequests.clear();
        playerOverrides.clear();
        playerOverrideRequests.clear();
        generation++;
        return success(null, "all", count);
    }

    public static synchronized void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (broadcastSelection != null && broadcastSelectionId != null) {
            transport().send(player, PolicyMessage.selection(broadcastSelectionId,
                    broadcastSelection.filter(), broadcastSelection.strength()));
        }
        broadcastOverrideRequests.forEach((owner, request) -> {
            UUID id = broadcastOverrides.get(owner);
            if (id != null) transport().send(player,
                    PolicyMessage.override(id, owner, request.filter(), request.strength(), request.priority()));
        });
        FilterSelectionRequest selection = playerSelections.get(player.getUUID());
        if (selection != null) transport().send(player,
                PolicyMessage.selection(UUID.randomUUID(), selection.filter(), selection.strength()));
        Map<String, UUID> overrides = playerOverrides.get(player.getUUID());
        Map<String, FilterOverrideRequest> requests = playerOverrideRequests.get(player.getUUID());
        if (overrides != null && requests != null) overrides.forEach((owner, id) -> {
            FilterOverrideRequest request = requests.get(owner);
            if (request != null) transport().send(player,
                    PolicyMessage.override(id, owner, request.filter(), request.strength(), request.priority()));
        });
    }

    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        initialized = false;
        generation = 0L;
        broadcastSelectionId = null;
        broadcastSelection = null;
        broadcastOverrides.clear();
        playerOverrides.clear();
        broadcastOverrideRequests.clear();
        playerOverrideRequests.clear();
        playerSelections.clear();
    }

    private static OperationResult success(UUID id, String target, int affected) {
        return new OperationResult(FilterOperationStatus.SUCCESS, FeedbackCode.OK, id, target, affected, Map.of(), generation);
    }

    private static OperationResult invalid(String target) {
        return OperationResult.failure(FilterOperationStatus.INVALID_ARGUMENT, FeedbackCode.INVALID_ARGUMENT, target, generation);
    }

    private static OperationResult notFound(String target) {
        return OperationResult.failure(FilterOperationStatus.NOT_FOUND, FeedbackCode.NOT_FOUND, target, generation);
    }

    private static PolicyTransport transport() {
        return transport;
    }

    private static void resendPersonalSelections() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FilterSelectionRequest personal = playerSelections.get(player.getUUID());
            if (personal != null) transport().send(player,
                    PolicyMessage.selection(UUID.randomUUID(), personal.filter(), personal.strength()));
        }
    }
}
