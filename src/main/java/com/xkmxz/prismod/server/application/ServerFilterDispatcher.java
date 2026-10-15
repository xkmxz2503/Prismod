package com.xkmxz.prismod.server.application;

import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.submit.result.FeedbackCode;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.server.query.request.ServerFilterQuery;
import com.xkmxz.prismod.api.server.query.view.ServerFilterStatus;
import com.xkmxz.prismod.api.server.query.view.ServerStatus;
import com.xkmxz.prismod.api.server.submit.model.ServerFilterTarget;
import com.xkmxz.prismod.api.server.submit.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.server.submit.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.server.submit.request.ServerFilterRequest;
import com.xkmxz.prismod.api.server.submit.result.OperationResult;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 服务端串行请求队列；持久策略身份独立于本次请求身份。 */
final class ServerFilterDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerFilterRuntime runtime;
    private final ArrayDeque<Work> queue = new ArrayDeque<>();
    private final Map<UUID, Selection> playerSelections = new LinkedHashMap<>();
    private final Map<String, OverridePolicy> globalOverrides = new LinkedHashMap<>();
    private final Map<UUID, Map<String, OverridePolicy>> playerOverrides = new LinkedHashMap<>();
    private Selection globalSelection;
    private long generation;
    private long epoch;
    private boolean scheduled;
    private volatile boolean initialized;
    private volatile ReadView published;

    ServerFilterDispatcher(ServerFilterRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        publish();
    }

    // 初始化和停止由服务端生命周期线程调用，所有业务写入由队列串行执行。
    void initialize() { initialized = true; publish(); }

    void stop() {
        List<Work> pending;
        synchronized (queue) {
            epoch++;
            pending = List.copyOf(queue);
            queue.clear();
            scheduled = false;
        }
        initialized = false;
        globalSelection = null;
        playerSelections.clear();
        globalOverrides.clear();
        playerOverrides.clear();
        generation = 0;
        publish();
        pending.forEach(work -> unavailable(work, "server stopped"));
    }

    CompletionStage<OperationResult> submit(ServerFilterRequest request) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        enqueue(UUID.randomUUID(), request, future, null);
        return future.minimalCompletionStage();
    }

    void replay(UUID player) { enqueue(UUID.randomUUID(), null, null, Objects.requireNonNull(player)); }

    @SuppressWarnings("unchecked")
    <R> R query(ServerFilterQuery<R> request) {
        Objects.requireNonNull(request, "request");
        ReadView view = published;
        if (request instanceof ServerFilterQuery.Status) return (R) view.status();
        if (request instanceof ServerFilterQuery.FilterStatus) return (R) view.filters();
        throw new IllegalArgumentException("unsupported query");
    }

    private void enqueue(UUID id, ServerFilterRequest request, CompletableFuture<OperationResult> future, UUID replay) {
        long scheduledEpoch;
        synchronized (queue) {
            scheduledEpoch = epoch;
            queue.add(new Work(id, request, future, replay));
            if (scheduled) return;
            scheduled = true;
        }
        try {
            runtime.execute(() -> drain(scheduledEpoch));
        } catch (RuntimeException error) {
            LOGGER.warn("Prismod server API scheduling unavailable", error);
            List<Work> rejected;
            synchronized (queue) {
                if (epoch != scheduledEpoch) return;
                rejected = List.copyOf(queue);
                queue.clear();
                scheduled = false;
            }
            rejected.forEach(work -> unavailable(work, "server scheduling unavailable"));
        }
    }

    private void drain(long scheduledEpoch) {
        while (true) {
            Work work;
            synchronized (queue) {
                if (epoch != scheduledEpoch) return;
                work = queue.poll();
                if (work == null) { scheduled = false; return; }
            }
            OperationResult result;
            List<Delivery> outgoing = new ArrayList<>();
            if (!initialized || !runtime.available()) {
                unavailable(work, "server unavailable");
                continue;
            }
            try {
                if (work.replay() != null) {
                    replayTo(work.replay(), outgoing);
                    result = null;
                } else result = apply(work.id(), Objects.requireNonNull(work.request(), "request"), outgoing);
            } catch (OfflinePlayer error) {
                result = failure(work.id(), FilterOperationStatus.NOT_FOUND, FeedbackCode.NOT_FOUND,
                        FilterFailureReason.INVALID_ARGUMENT, "player is offline", error.player.toString());
            } catch (IllegalArgumentException | NullPointerException error) {
                result = failure(work.id(), FilterOperationStatus.INVALID_ARGUMENT, FeedbackCode.INVALID_ARGUMENT,
                        FilterFailureReason.INVALID_ARGUMENT, error.getMessage(), "");
            } catch (RuntimeException error) {
                LOGGER.warn("Prismod server request failed", error);
                result = failure(work.id(), FilterOperationStatus.FAILED, FeedbackCode.FAILED,
                        FilterFailureReason.UNKNOWN, "server operation failed", "");
            }
            publish();
            // 回执描述本地提交。发送失败不能把已保存策略伪装成未提交。
            for (Delivery delivery : outgoing) {
                try { runtime.send(delivery.player(), delivery.message()); }
                catch (RuntimeException error) { LOGGER.warn("Prismod policy delivery failed", error); }
            }
            if (work.future() != null) work.future().complete(result);
        }
    }

    private OperationResult apply(UUID id, ServerFilterRequest request, List<Delivery> outgoing) {
        if (request instanceof ServerFilterRequest.Select r) {
            UUID player = targetPlayer(r.target());
            FilterSelectionRequest params = Objects.requireNonNull(r.selection(), "selection");
            Selection old = player == null ? globalSelection : playerSelections.get(player);
            Selection policy = new Selection(old == null ? UUID.randomUUID() : old.id(), params);
            if (player == null) globalSelection = policy;
            else playerSelections.put(player, policy);
            generation++;
            outgoing.add(new Delivery(player, selectionMessage(id, policy)));
            if (player == null) {
                for (UUID online : runtime.onlinePlayers()) {
                    Selection personal = playerSelections.get(online);
                    if (personal != null) outgoing.add(new Delivery(online, selectionMessage(id, personal)));
                }
            }
            return success(id, describe(player), 1, List.of(policy.id()));
        }
        if (request instanceof ServerFilterRequest.Override r) {
            UUID player = targetPlayer(r.target());
            FilterOverrideRequest params = Objects.requireNonNull(r.override(), "override");
            Map<String, OverridePolicy> policies = player == null ? globalOverrides
                    : playerOverrides.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
            OverridePolicy old = policies.get(params.ownerId());
            OverridePolicy policy = new OverridePolicy(old == null ? UUID.randomUUID() : old.id(), params, ++generation);
            policies.put(params.ownerId(), policy);
            outgoing.add(new Delivery(player, overrideMessage(id, policy)));
            return success(id, describe(player), 1, List.of(policy.id()));
        }
        if (request instanceof ServerFilterRequest.ClearOverride r) {
            UUID player = targetPlayer(r.target());
            String owner = requireOwner(r.ownerId());
            Map<String, OverridePolicy> policies = player == null ? globalOverrides : playerOverrides.get(player);
            OverridePolicy removed = policies == null ? null : policies.remove(owner);
            if (player != null && policies != null && policies.isEmpty()) playerOverrides.remove(player);
            if (removed != null) {
                generation++;
                outgoing.add(new Delivery(player, PolicyMessage.clearOverride(id, removed.id(), owner)));
            }
            return success(id, describe(player), removed == null ? 0 : 1, List.of());
        }
        if (request instanceof ServerFilterRequest.ClearAllOverrides) {
            int count = globalOverrides.size() + playerOverrides.values().stream().mapToInt(Map::size).sum();
            if (count > 0) {
                globalOverrides.clear(); playerOverrides.clear(); generation++;
                outgoing.add(new Delivery(null, PolicyMessage.clearAllOverrides(id)));
            }
            return success(id, "all", count, List.of());
        }
        if (request instanceof ServerFilterRequest.ClearSelection) {
            int count = playerSelections.size() + (globalSelection == null ? 0 : 1);
            if (count > 0) {
                globalSelection = null; playerSelections.clear(); generation++;
                outgoing.add(new Delivery(null, PolicyMessage.clearSelection(id)));
            }
            return success(id, "all", count, List.of());
        }
        throw new IllegalArgumentException("unsupported request");
    }

    private void replayTo(UUID player, List<Delivery> outgoing) {
        if (!runtime.onlinePlayers().contains(player)) return;
        if (globalSelection != null) outgoing.add(new Delivery(player, selectionMessage(UUID.randomUUID(), globalSelection)));
        Selection personal = playerSelections.get(player);
        if (personal != null) outgoing.add(new Delivery(player, selectionMessage(UUID.randomUUID(), personal)));
        // 按原提交次序重建覆盖，维持客户端同优先级时较新者胜出的规则。
        List<OverridePolicy> policies = new ArrayList<>(globalOverrides.values());
        policies.addAll(playerOverrides.getOrDefault(player, Map.of()).values());
        policies.stream().sorted(Comparator.comparingLong(OverridePolicy::order)).forEach(policy ->
                outgoing.add(new Delivery(player, overrideMessage(UUID.randomUUID(), policy))));
    }

    private UUID targetPlayer(ServerFilterTarget target) {
        Objects.requireNonNull(target, "target");
        if (target instanceof ServerFilterTarget.Broadcast) return null;
        UUID player = Objects.requireNonNull(((ServerFilterTarget.Player) target).playerId(), "playerId");
        if (!runtime.onlinePlayers().contains(player)) throw new OfflinePlayer(player);
        return player;
    }

    private void publish() {
        published = new ReadView(new ServerStatus(initialized, ResourceLocation.fromNamespaceAndPath(
                PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH), PolicyTransport.PROTOCOL_VERSION),
                new ServerFilterStatus(globalSelection == null ? null : globalSelection.request().filter(),
                        globalSelection == null ? 0 : globalSelection.request().strength(), globalOverrides.size(),
                        playerSelections.size(), playerOverrides.values().stream().mapToInt(Map::size).sum(), generation));
    }

    private static PolicyMessage selectionMessage(UUID id, Selection policy) {
        return PolicyMessage.selection(id, policy.id(), policy.request().filter(), policy.request().strength());
    }
    private static PolicyMessage overrideMessage(UUID id, OverridePolicy policy) {
        var params = policy.request();
        return PolicyMessage.override(id, policy.id(), params.ownerId(), params.filter(), params.strength(), params.priority());
    }
    private static String requireOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        return owner.trim();
    }
    private static String describe(UUID player) { return player == null ? "broadcast" : player.toString(); }
    private OperationResult success(UUID id, String target, int count, List<UUID> policies) {
        return new OperationResult(id, FilterOperationStatus.SUCCESS, FeedbackCode.OK,
                FilterFailureReason.NONE, "", target, count, policies, generation);
    }
    private OperationResult failure(UUID id, FilterOperationStatus status, FeedbackCode code,
                                    FilterFailureReason reason, String detail, String target) {
        return new OperationResult(id, status, code, reason, detail, target, 0, List.of(), published.filters().generation());
    }
    private void unavailable(Work work, String detail) {
        if (work.future() != null) work.future().complete(failure(work.id(), FilterOperationStatus.UNAVAILABLE,
                FeedbackCode.UNAVAILABLE, FilterFailureReason.UNKNOWN, detail, ""));
    }
    private record Work(UUID id, ServerFilterRequest request, CompletableFuture<OperationResult> future, UUID replay) { }
    private record Delivery(UUID player, PolicyMessage message) { }
    private record Selection(UUID id, FilterSelectionRequest request) { }
    private record OverridePolicy(UUID id, FilterOverrideRequest request, long order) { }
    private record ReadView(ServerStatus status, ServerFilterStatus filters) { }
    private static final class OfflinePlayer extends RuntimeException {
        final UUID player;
        OfflinePlayer(UUID player) { this.player = player; }
    }
}
