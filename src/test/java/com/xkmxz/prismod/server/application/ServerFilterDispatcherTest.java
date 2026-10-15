package com.xkmxz.prismod.server.application;

import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.server.query.request.ServerFilterQuery;
import com.xkmxz.prismod.api.server.submit.model.ServerFilterTarget;
import com.xkmxz.prismod.api.server.submit.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.server.submit.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.server.submit.request.ServerFilterRequest;
import com.xkmxz.prismod.api.server.submit.result.OperationResult;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerFilterDispatcherTest {
    private static final ResourceLocation WARM = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
    private static final ResourceLocation COOL = ResourceLocation.fromNamespaceAndPath("prismod", "cool");
    private static final ServerFilterTarget BROADCAST = new ServerFilterTarget.Broadcast();

    @Test void queuedRequestPublishesBeforeFinalReceiptAndExecutesOnce() {
        Harness h = new Harness();
        var stage = h.api.submit(select(BROADCAST, WARM));
        assertFalse(stage.toCompletableFuture().isDone());
        assertNull(h.api.query(new ServerFilterQuery.FilterStatus()).globalSelection());
        var observed = new ArrayList<ResourceLocation>();
        stage.thenAccept(r -> observed.add(h.api.query(new ServerFilterQuery.FilterStatus()).globalSelection()));
        h.drain(); h.drain();
        assertEquals(List.of(WARM), observed);
        assertEquals(1, h.sent.size());
        assertEquals(1, stage.toCompletableFuture().join().generation());
        assertNotEquals(new UUID(0, 0), stage.toCompletableFuture().join().requestId());
    }

    @Test void sameOwnerUpdatesOnePolicyWithIndependentRequestsAndNewPayload() {
        Harness h = new Harness();
        OperationResult first = h.run(override(BROADCAST, "weather", WARM, 10));
        OperationResult second = h.run(override(BROADCAST, "weather", COOL, 20));
        assertNotEquals(first.requestId(), second.requestId());
        assertEquals(first.policyIds(), second.policyIds());
        assertEquals(1, h.api.query(new ServerFilterQuery.FilterStatus()).globalOverrideCount());
        assertEquals(COOL, h.sent.get(1).message().filter());
        assertEquals(20, h.sent.get(1).message().priority());
        assertEquals(second.requestId(), h.sent.get(1).message().requestId());
        assertEquals(second.policyIds().get(0), h.sent.get(1).message().policyId());
    }

    @Test void scopesRemainIndependentAndRepeatedClearIsSuccessfulNoOp() {
        Harness h = new Harness(); UUID player = UUID.randomUUID(); h.online.add(player);
        var personal = new ServerFilterTarget.Player(player);
        var global = h.run(override(BROADCAST, "weather", WARM, 10));
        var local = h.run(override(personal, "weather", COOL, 10));
        assertNotEquals(global.policyIds(), local.policyIds());
        assertEquals(1, h.run(new ServerFilterRequest.ClearOverride(BROADCAST, "weather")).affectedCount());
        assertEquals(0, h.run(new ServerFilterRequest.ClearOverride(BROADCAST, "weather")).affectedCount());
        assertEquals(1, h.api.query(new ServerFilterQuery.FilterStatus()).playerOverrideCount());
        assertEquals(1, h.run(new ServerFilterRequest.ClearAllOverrides()).affectedCount());
        assertEquals(0, h.run(new ServerFilterRequest.ClearAllOverrides()).affectedCount());
    }

    @Test void unavailableAndOfflineRequestsDoNotSavePolicies() {
        Harness h = new Harness(); h.available = false;
        var unavailable = h.run(select(BROADCAST, WARM));
        assertEquals(FilterOperationStatus.UNAVAILABLE, unavailable.status());
        h.available = true;
        var offline = h.run(select(new ServerFilterTarget.Player(UUID.randomUUID()), COOL));
        assertEquals(FilterOperationStatus.NOT_FOUND, offline.status());
        assertNotEquals(unavailable.requestId(), offline.requestId());
        assertEquals(0, h.api.query(new ServerFilterQuery.FilterStatus()).generation());
        assertTrue(h.sent.isEmpty());
    }

    @Test void invalidRequestAndRejectedSchedulingReturnFinalFailures() {
        Harness h = new Harness();
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT, h.run(null).status());
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT,
                h.run(new ServerFilterRequest.Select(null, new FilterSelectionRequest(WARM, 1))).status());
        h.reject = true;
        assertEquals(FilterOperationStatus.UNAVAILABLE,
                h.api.submit(select(BROADCAST, WARM)).toCompletableFuture().join().status());
    }

    @Test void cancellingDerivedFutureDoesNotCancelBusinessAndCallbacksQueueWrites() {
        Harness h = new Harness();
        var first = h.api.submit(select(BROADCAST, WARM));
        first.toCompletableFuture().cancel(false);
        first.thenAccept(r -> {
            h.api.submit(select(BROADCAST, COOL));
            assertEquals(WARM, h.api.query(new ServerFilterQuery.FilterStatus()).globalSelection());
        });
        h.drain();
        assertEquals(COOL, h.api.query(new ServerFilterQuery.FilterStatus()).globalSelection());
        assertEquals(2, h.sent.size());
    }

    @Test void loginReplaysStablePolicyIdsWithFreshRequestsAndPersonalSelectionLast() {
        Harness h = new Harness(); UUID player = UUID.randomUUID(); h.online.add(player);
        var target = new ServerFilterTarget.Player(player);
        h.run(select(BROADCAST, WARM));
        var personal = h.run(select(target, COOL));
        var first = h.run(override(target, "first", WARM, 10));
        var last = h.run(override(BROADCAST, "last", COOL, 10));
        h.sent.clear(); h.api.replay(player); h.drain();
        var selections = h.sent.stream().map(Delivery::message)
                .filter(m -> m.operation() == PolicyMessage.Operation.SELECT).toList();
        assertEquals(List.of(WARM, COOL), selections.stream().map(PolicyMessage::filter).toList());
        assertEquals(personal.policyIds().get(0), selections.get(1).policyId());
        assertNotEquals(personal.requestId(), selections.get(1).requestId());
        var overrides = h.sent.stream().map(Delivery::message)
                .filter(m -> m.operation() == PolicyMessage.Operation.OVERRIDE).toList();
        assertEquals(List.of(first.policyIds().get(0), last.policyIds().get(0)),
                overrides.stream().map(PolicyMessage::policyId).toList());
        assertTrue(h.sent.stream().allMatch(d -> player.equals(d.player())));
    }

    @Test void broadcastingSelectionReassertsPersonalChoice() {
        Harness h = new Harness(); UUID player = UUID.randomUUID(); h.online.add(player);
        h.run(select(new ServerFilterTarget.Player(player), COOL)); h.sent.clear();
        h.run(select(BROADCAST, WARM));
        assertEquals(2, h.sent.size());
        assertNull(h.sent.get(0).player());
        assertEquals(player, h.sent.get(1).player());
        assertEquals(COOL, h.sent.get(1).message().filter());
    }

    @Test void stoppingFailsPendingWorkAndOldDrainCannotExecuteAfterRestart() {
        Harness h = new Harness();
        var old = h.api.submit(select(BROADCAST, WARM));
        h.api.stop();
        assertEquals(FilterOperationStatus.UNAVAILABLE, old.toCompletableFuture().join().status());
        h.api.initialize();
        var fresh = h.api.submit(select(BROADCAST, COOL)); h.drain();
        assertEquals(FilterOperationStatus.SUCCESS, fresh.toCompletableFuture().join().status());
        assertEquals(1, h.sent.size());
        assertEquals(COOL, h.api.query(new ServerFilterQuery.FilterStatus()).globalSelection());
    }

    @Test void transportFailureDoesNotChangeMeaningOfCommittedReceipt() {
        Harness h = new Harness(); h.failSend = true;
        assertEquals(FilterOperationStatus.SUCCESS, h.run(select(BROADCAST, WARM)).status());
        assertEquals(WARM, h.api.query(new ServerFilterQuery.FilterStatus()).globalSelection());
    }

    private static ServerFilterRequest select(ServerFilterTarget target, ResourceLocation filter) {
        return new ServerFilterRequest.Select(target, new FilterSelectionRequest(filter, .75F));
    }
    private static ServerFilterRequest override(ServerFilterTarget target, String owner, ResourceLocation filter, int priority) {
        return new ServerFilterRequest.Override(target, new FilterOverrideRequest(owner, filter, .6F, priority));
    }
    private record Delivery(UUID player, PolicyMessage message) { }
    private static final class Harness implements ServerFilterRuntime {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final List<Delivery> sent = new ArrayList<>();
        final Set<UUID> online = new LinkedHashSet<>();
        final ServerFilterDispatcher api = new ServerFilterDispatcher(this);
        boolean available = true, reject, failSend;
        Harness() { api.initialize(); }
        @Override public void execute(Runnable task) {
            if (reject) throw new RejectedExecutionException();
            tasks.add(task);
        }
        @Override public boolean available() { return available; }
        @Override public List<UUID> onlinePlayers() { return List.copyOf(online); }
        @Override public void send(UUID player, PolicyMessage message) {
            if (failSend) throw new IllegalStateException("send failed");
            sent.add(new Delivery(player, message));
        }
        void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
        OperationResult run(ServerFilterRequest request) {
            var result = api.submit(request); drain(); return result.toCompletableFuture().join();
        }
    }
}
