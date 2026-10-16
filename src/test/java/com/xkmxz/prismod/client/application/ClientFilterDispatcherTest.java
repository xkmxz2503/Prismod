package com.xkmxz.prismod.client.application;

import com.xkmxz.prismod.api.client.submit.model.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;
import com.xkmxz.prismod.api.client.submit.result.FilterReceipt;
import com.xkmxz.prismod.api.client.submit.listener.FilterStateListener;
import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.query.view.FilterResourceView;
import com.xkmxz.prismod.api.client.submit.event.FilterEvent;
import com.xkmxz.prismod.api.client.submit.event.FilterEventType;
import com.xkmxz.prismod.api.client.submit.listener.FilterEventListener;
import com.xkmxz.prismod.api.client.query.request.FilterQuery;
import com.xkmxz.prismod.api.client.submit.request.FilterRequest;
import com.xkmxz.prismod.api.client.query.view.FilterDescriptor;
import com.xkmxz.prismod.api.common.submit.result.FeedbackCode;
import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.client.query.state.filter.FilterFallbackReason;
import com.xkmxz.prismod.api.client.query.state.lifecycle.*;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.client.application.handle.FilterOverride;
import com.xkmxz.prismod.client.application.handle.FilterRegistration;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ClientFilterDispatcherTest {
    private static final ResourceLocation FILTER = id("warm");
    private static ResourceLocation id(String value) {
        return ResourceLocation.fromNamespaceAndPath("example", value);
    }

    @Test
    void queuesOnceAndQueriesNeverExecutePendingWork() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        MemoryBackend backend = new MemoryBackend();
        ClientFilterDispatcher api = new ClientFilterDispatcher(tasks::add, backend);
        var pending = api.submit(new FilterRequest.Select(FILTER, 2));
        assertFalse(pending.toCompletableFuture().isDone());
        assertEquals(id("original"), api.query(new FilterQuery.Snapshot()).filter());
        assertEquals(1, tasks.size());
        tasks.remove().run();
        FilterReceipt receipt = pending.toCompletableFuture().join();
        assertEquals(FilterOperationStatus.SUCCESS, receipt.status());
        assertEquals(1, backend.writes);
        assertEquals(2, api.query(new FilterQuery.Snapshot()).strength());
        assertEquals(FILTER, api.query(new FilterQuery.Snapshot()).filter());
        assertTrue(tasks.isEmpty());
    }

    @Test
    void forwardsOptionalAdjustmentsWithoutChangingLegacyRequests() {
        MemoryBackend backend = new MemoryBackend();
        ClientFilterDispatcher api = new ClientFilterDispatcher(Runnable::run, backend);
        FilterAdjustments adjustments = FilterAdjustments.builder().exposure(100F).build();

        submit(api, new FilterRequest.Select(FILTER, 80F, adjustments));
        assertSame(adjustments, backend.selectedAdjustments);
        submit(api, new FilterRequest.Force(FILTER, 70F));
        assertNull(backend.forcedAdjustments);
        submit(api, new FilterRequest.Override("owner", FILTER, 60F, 10, adjustments));
        assertSame(adjustments, backend.overrideAdjustments);
    }

    @Test
    void callerCannotCancelTheSubmittedBusinessByCancellingItsFutureCopy() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        MemoryBackend backend = new MemoryBackend();
        ClientFilterDispatcher api = new ClientFilterDispatcher(tasks::add, backend);
        var submitted = api.submit(new FilterRequest.Select(FILTER, Float.NaN));
        submitted.toCompletableFuture().cancel(false);
        tasks.remove().run();
        assertEquals(1, backend.writes);
        assertEquals(0, api.query(new FilterQuery.Snapshot()).strength());
        assertEquals(FilterOperationStatus.SUCCESS, submitted.toCompletableFuture().join().status());
    }

    @Test
    void receiptIsFinalAndResourceReleaseIsIdempotent() {
        ClientFilterDispatcher api = direct();
        FilterReceipt created = submit(api, new FilterRequest.Override("owner", FILTER, 50F, 10));
        UUID resource = only(created);
        assertEquals(FilterResourceView.Kind.OVERRIDE, api.query(new FilterQuery.Resource(resource)).orElseThrow().kind());
        assertEquals(1, submit(api, new FilterRequest.Release(resource)).affectedCount());
        assertTrue(api.query(new FilterQuery.Resource(resource)).isEmpty());
        assertEquals(0, submit(api, new FilterRequest.Release(resource)).affectedCount());
        assertEquals(FilterOperationStatus.SUCCESS, created.status());
        assertEquals(List.of(resource), created.resourceIds());
    }

    @Test
    void sessionsWithSameOwnerDoNotClearEachOtherOrIndependentResources() {
        ClientFilterDispatcher api = direct();
        UUID first = only(submit(api, new FilterRequest.OpenSession("owner")));
        UUID second = only(submit(api, new FilterRequest.OpenSession("owner")));
        UUID a = only(submit(api, new FilterRequest.Override("owner", FILTER, 1, 1, first)));
        UUID b = only(submit(api, new FilterRequest.Override("owner", FILTER, 1, 2, second)));
        UUID independent = only(submit(api, new FilterRequest.Override("owner", FILTER, 1, 3)));
        submit(api, new FilterRequest.CloseSession(first));
        assertTrue(api.query(new FilterQuery.Resource(a)).isEmpty());
        assertTrue(api.query(new FilterQuery.Resource(first)).isEmpty());
        assertTrue(api.query(new FilterQuery.Resource(b)).isPresent());
        assertTrue(api.query(new FilterQuery.Resource(independent)).isPresent());
        assertEquals(FilterOperationStatus.CLOSED,
                submit(api, new FilterRequest.Override("owner", FILTER, 1, 1, first)).status());
        assertEquals(0, submit(api, new FilterRequest.CloseSession(first)).affectedCount());
    }

    @Test
    void clearSessionLeavesSessionReusableAndOwnerMustMatch() {
        ClientFilterDispatcher api = direct();
        UUID session = only(submit(api, new FilterRequest.OpenSession(" owner ")));
        submit(api, new FilterRequest.Override("owner", FILTER, 1, 1, session));
        assertEquals(1, submit(api, new FilterRequest.ClearSession(session)).affectedCount());
        assertTrue(api.query(new FilterQuery.SessionResources(session)).isEmpty());
        assertTrue(api.query(new FilterQuery.Resource(session)).isPresent());
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT,
                submit(api, new FilterRequest.Override("another", FILTER, 1, 1, session)).status());
        assertEquals(FilterOperationStatus.SUCCESS,
                submit(api, new FilterRequest.Override("owner", FILTER, 1, 1, session)).status());
    }

    @Test
    void batchPreservesOrderAndReportsMixedFailuresWithoutRollingBack() {
        ClientFilterDispatcher api = direct();
        UUID session = only(submit(api, new FilterRequest.OpenSession("owner")));
        List<FilterRequest> requests = Arrays.asList(
                new FilterRequest.Override("owner", FILTER, 1, 2),
                null,
                new FilterRequest.Override("different-owner", FILTER, 1, 3),
                new FilterRequest.Force(FILTER, 1),
                new FilterRequest.Register("owner", id("shaders/post/custom.json"), null));
        FilterReceipt batch = submit(api, new FilterRequest.Batch(session, requests));
        assertEquals(FilterOperationStatus.PARTIAL, batch.status());
        assertEquals(List.of(FilterOperationStatus.SUCCESS, FilterOperationStatus.INVALID_ARGUMENT,
                FilterOperationStatus.INVALID_ARGUMENT, FilterOperationStatus.INVALID_ARGUMENT,
                FilterOperationStatus.SUCCESS), batch.items().stream().map(FilterReceipt::status).toList());
        assertEquals(2, batch.affectedCount());
        assertEquals(2, api.query(new FilterQuery.SessionResources(session)).size());
        assertEquals(FilterOperationStatus.SUCCESS,
                submit(api, new FilterRequest.Batch(session, List.of())).status());
    }

    @Test
    void sessionCloseAfterQueuedBatchReleasesItsResults() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        ClientFilterDispatcher api = new ClientFilterDispatcher(tasks::add, new MemoryBackend());
        var open = api.submit(new FilterRequest.OpenSession("owner"));
        tasks.remove().run();
        UUID session = only(open.toCompletableFuture().join());
        var batch = api.submit(new FilterRequest.Batch(session,
                List.of(new FilterRequest.Override("owner", FILTER, 1, 3))));
        var closed = api.submit(new FilterRequest.CloseSession(session));
        tasks.remove().run();
        assertEquals(FilterOperationStatus.SUCCESS, batch.toCompletableFuture().join().status());
        assertEquals(2, closed.toCompletableFuture().join().affectedCount());
        assertTrue(api.query(new FilterQuery.Resources("owner")).isEmpty());
    }

    @Test
    void replacedRegistrationCannotBeRemovedUsingOldResourceId() {
        ClientFilterDispatcher api = direct();
        UUID first = only(submit(api, new FilterRequest.Register("owner", FILTER, id("a.json"), null)));
        UUID second = only(submit(api, new FilterRequest.Register("owner", FILTER, id("b.json"), null)));
        assertTrue(api.query(new FilterQuery.Resource(first)).isEmpty());
        assertEquals(0, submit(api, new FilterRequest.Release(first)).affectedCount());
        assertTrue(api.query(new FilterQuery.Resource(second)).isPresent());
        FilterReceipt conflict = submit(api, new FilterRequest.Register("another", FILTER, id("c.json"), null));
        assertEquals(FilterOperationStatus.FAILED, conflict.status());
        assertEquals(FilterFailureReason.ID_CONFLICT, conflict.failureReason());
        assertTrue(api.query(new FilterQuery.Resource(second)).isPresent());
    }

    @Test
    void clearOwnerRemovesResourceViewsAndAllowsSafeSessionClose() {
        ClientFilterDispatcher api = direct();
        UUID session = only(submit(api, new FilterRequest.OpenSession("owner")));
        submit(api, new FilterRequest.Override("owner", FILTER, 1, 1, session));
        submit(api, new FilterRequest.Register("owner", id("registered"), id("a.json"), null, session));
        assertEquals(1, submit(api, new FilterRequest.ClearOverrides("owner")).affectedCount());
        assertEquals(1, submit(api, new FilterRequest.ClearRegistrations("owner")).affectedCount());
        assertTrue(api.query(new FilterQuery.SessionResources(session)).isEmpty());
        assertEquals(1, submit(api, new FilterRequest.CloseSession(session)).affectedCount());
    }

    @Test
    void clearOwnerAlsoReleasesFailedRegistrationResources() {
        ClientFilterDispatcher api = direct();
        UUID existing = only(submit(api, new FilterRequest.Register("first", FILTER, id("a.json"), null)));
        UUID session = only(submit(api, new FilterRequest.OpenSession("conflicting")));
        FilterReceipt failed = submit(api, new FilterRequest.Register("conflicting", FILTER,
                id("b.json"), null, session));
        assertEquals(FilterOperationStatus.FAILED, failed.status());
        assertEquals(FilterResourceView.State.FAILED,
                api.query(new FilterQuery.Resource(only(failed))).orElseThrow().state());
        submit(api, new FilterRequest.ClearRegistrations("conflicting"));
        assertTrue(api.query(new FilterQuery.SessionResources(session)).isEmpty());
        assertTrue(api.query(new FilterQuery.Resource(only(failed))).isEmpty());
        assertTrue(api.query(new FilterQuery.Resource(existing)).isPresent());
    }

    @Test
    void publishesBeforeCompletionEventsAndSerializesCallbackSubmissions() {
        ClientFilterDispatcher api = direct();
        List<UUID> completed = new ArrayList<>();
        List<CompletionStage<FilterReceipt>> releases = new ArrayList<>();
        submit(api, new FilterRequest.SubscribeEvents("observer", event -> {
            if (event.receipt() != null) {
                completed.add(event.receipt().requestId());
                for (UUID resource : event.receipt().resourceIds()) {
                    var view = api.query(new FilterQuery.Resource(resource));
                    if (view.isPresent() && view.get().kind() == FilterResourceView.Kind.OVERRIDE) {
                        releases.add(api.submit(new FilterRequest.Release(resource)));
                        assertTrue(api.query(new FilterQuery.Resource(resource)).isPresent());
                    }
                }
            }
        }));
        FilterReceipt created = submit(api, new FilterRequest.Override("owner", FILTER, 1, 1));
        assertEquals(1, Collections.frequency(completed, created.requestId()));
        assertTrue(api.query(new FilterQuery.Resource(only(created))).isEmpty());
        assertEquals(1, releases.size());
        assertEquals(1, releases.get(0).toCompletableFuture().join().affectedCount());
    }

    @Test
    void stateListenerGetsNoInitialReplayAndCallbackWritesDoNotNest() {
        ClientFilterDispatcher api = direct();
        AtomicInteger depth = new AtomicInteger();
        AtomicInteger maximumDepth = new AtomicInteger();
        List<ResourceLocation> states = new ArrayList<>();
        UUID subscription = only(submit(api, new FilterRequest.SubscribeState("observer", snapshot -> {
            maximumDepth.accumulateAndGet(depth.incrementAndGet(), Math::max);
            states.add(snapshot.filter());
            assertEquals(snapshot, api.query(new FilterQuery.Snapshot()));
            if (states.size() == 1) api.submit(new FilterRequest.Select(id("second"), 50F));
            depth.decrementAndGet();
        })));
        assertTrue(states.isEmpty());
        submit(api, new FilterRequest.Select(FILTER, 1));
        assertEquals(List.of(FILTER, id("second")), states);
        assertEquals(1, maximumDepth.get());
        submit(api, new FilterRequest.Release(subscription));
        submit(api, new FilterRequest.Select(FILTER, 1));
        assertEquals(2, states.size());
    }

    @Test
    void listenerFailureDoesNotPreventOtherListenersOrFinalReceipt() {
        ClientFilterDispatcher api = direct();
        AtomicInteger received = new AtomicInteger();
        submit(api, new FilterRequest.SubscribeState("broken", snapshot -> { throw new IllegalStateException("test"); }));
        submit(api, new FilterRequest.SubscribeState("working", snapshot -> received.incrementAndGet()));
        assertEquals(FilterOperationStatus.SUCCESS, submit(api, new FilterRequest.Select(FILTER, 1)).status());
        assertEquals(1, received.get());
    }

    @Test
    void queryListsAreImmutableAndOldViewsDoNotChange() {
        ClientFilterDispatcher api = direct();
        List<FilterResourceView> old = api.query(new FilterQuery.Resources("owner"));
        submit(api, new FilterRequest.Override("owner", FILTER, 1, 1));
        assertTrue(old.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> api.query(new FilterQuery.Resources("owner")).clear());
        assertThrows(UnsupportedOperationException.class,
                () -> api.query(new FilterQuery.Filters(false)).clear());
    }

    @Test
    void invalidRequestsAndSchedulingFailureHaveTerminalReceipts() {
        ClientFilterDispatcher api = direct();
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT, submit(api, null).status());
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT, submit(api, new FilterRequest.Select(null, 1)).status());
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT, submit(api, new FilterRequest.OpenSession(" ")).status());
        assertEquals(FilterOperationStatus.INVALID_ARGUMENT, submit(api, new FilterRequest.Release(null)).status());
        ClientFilterDispatcher unavailable = new ClientFilterDispatcher(task -> {
            throw new RejectedExecutionException("test");
        }, new MemoryBackend());
        assertEquals(FilterOperationStatus.UNAVAILABLE,
                submit(unavailable, new FilterRequest.ClearForced()).status());
    }

    @Test
    void concurrentReadersOnlySeeCompletePublishedViews() throws Exception {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        ClientFilterDispatcher api = new ClientFilterDispatcher(tasks::add, new MemoryBackend());
        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 1000; i++) {
                assertNotNull(api.query(new FilterQuery.Snapshot()).selectedFilter());
                for (FilterResourceView view : api.query(new FilterQuery.Resources("owner"))) {
                    assertNotNull(view.resourceId());
                    assertNotNull(view.kind());
                }
            }
        });
        for (int i = 0; i < 100; i++) {
            api.submit(new FilterRequest.Override("owner", FILTER, 1, i));
            tasks.remove().run();
        }
        reader.get(5, TimeUnit.SECONDS);
    }

    private static ClientFilterDispatcher direct() {
        return new ClientFilterDispatcher(Runnable::run, new MemoryBackend());
    }
    private static FilterReceipt submit(ClientFilterDispatcher api, FilterRequest request) {
        return api.submit(request).toCompletableFuture().join();
    }
    private static UUID only(FilterReceipt receipt) {
        assertEquals(1, receipt.resourceIds().size());
        return receipt.resourceIds().get(0);
    }

    /** 不依赖 Minecraft 单例的内存端口，用于检验真实分派器的执行与资源管理。 */
    private static final class MemoryBackend implements FilterBackend {
        private FilterSnapshot snapshot = new FilterSnapshot(id("original"), 0, false, true);
        private Consumer<FilterEvent> observer;
        private final Map<ResourceLocation, Registration> registrations = new HashMap<>();
        private final List<OverrideHandle> overrides = new ArrayList<>();
        private FilterAdjustments selectedAdjustments;
        private FilterAdjustments forcedAdjustments;
        private FilterAdjustments overrideAdjustments;
        private int writes;
        @Override public FilterSnapshot snapshot() { return snapshot; }
        @Override public List<FilterDescriptor> filters() { return List.of(); }
        @Override public void observe(Consumer<FilterEvent> observer) { this.observer = observer; }
        private void change(ResourceLocation filter, float strength, boolean forced) {
            writes++;
            snapshot = new FilterSnapshot(filter, strength, forced, true, filter, strength,
                    "", 0, FilterFallbackReason.NONE, snapshot.generation() + 1);
            observer.accept(FilterEvent.snapshotChanged(snapshot));
        }
        @Override public void select(ResourceLocation filter, float strength, FilterAdjustments adjustments) {
            selectedAdjustments = adjustments;
            change(filter, strength, false);
        }
        @Override public void clearSelection() { change(id("original"), 0, false); }
        @Override public void force(ResourceLocation filter, float strength, FilterAdjustments adjustments) {
            forcedAdjustments = adjustments;
            change(filter, strength, true);
        }
        @Override public void clearForced() { change(id("original"), 0, false); }
        @Override public FilterOverride override(String owner, ResourceLocation filter, float strength, int priority,
                                                 FilterAdjustments adjustments) {
            overrideAdjustments = adjustments;
            OverrideHandle handle = new OverrideHandle(owner, filter, priority);
            overrides.add(handle);
            change(filter, strength, true);
            return handle;
        }
        @Override public int clearOverrides(String owner) {
            int count = 0;
            for (OverrideHandle handle : overrides) {
                if (handle.active && handle.owner.equals(owner)) { handle.active = false; count++; }
            }
            return count;
        }
        @Override public FilterRegistration register(String owner, ResourceLocation logical,
                                                     ResourceLocation postEffect, CustomFilterMetadata metadata) {
            Registration previous = registrations.get(logical);
            if (previous != null && !previous.owner.equals(owner)) return new Registration(owner, logical, true);
            Registration next = new Registration(owner, logical, false);
            registrations.put(logical, next);
            observer.accept(FilterEvent.registryChanged(filters()));
            return next;
        }
        @Override public int clearRegistrations(String owner) {
            int before = registrations.size();
            registrations.values().removeIf(r -> r.owner.equals(owner));
            return before - registrations.size();
        }
        private final class Registration implements FilterRegistration {
            final String owner;
            final ResourceLocation filter;
            final boolean conflict;
            boolean closed;
            Registration(String owner, ResourceLocation filter, boolean conflict) {
                this.owner = owner; this.filter = filter; this.conflict = conflict;
            }
            @Override public ResourceLocation id() { return filter; }
            @Override public String ownerId() { return owner; }
            @Override public FilterRegistrationState state() {
                if (closed) return FilterRegistrationState.CLOSED;
                if (conflict) return FilterRegistrationState.FAILED;
                return registrations.get(filter) == this ? FilterRegistrationState.ACTIVE : FilterRegistrationState.CLOSED;
            }
            @Override public FilterFailureReason failureReason() {
                return conflict ? FilterFailureReason.ID_CONFLICT : FilterFailureReason.NONE;
            }
            @Override public void close() { closed = true; registrations.remove(filter, this); }
        }
        private static final class OverrideHandle implements FilterOverride {
            final String owner;
            final ResourceLocation filter;
            final int priority;
            boolean active = true;
            OverrideHandle(String owner, ResourceLocation filter, int priority) {
                this.owner = owner; this.filter = filter; this.priority = priority;
            }
            @Override public ResourceLocation id() { return filter; }
            @Override public String ownerId() { return owner; }
            @Override public int priority() { return priority; }
            @Override public FilterOverrideState state() {
                return active ? FilterOverrideState.ACTIVE : FilterOverrideState.CLOSED;
            }
            @Override public boolean isActive() { return active; }
            @Override public void close() { active = false; }
        }
    }
}
