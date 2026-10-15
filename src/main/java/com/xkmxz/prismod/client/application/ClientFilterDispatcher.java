package com.xkmxz.prismod.client.application;

import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.api.client.submit.model.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.submit.result.FilterReceipt;
import com.xkmxz.prismod.api.client.submit.listener.FilterStateListener;
import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.query.view.FilterResourceView;
import com.xkmxz.prismod.api.client.submit.event.FilterEvent;
import com.xkmxz.prismod.api.client.submit.event.FilterEventType;
import com.xkmxz.prismod.api.client.submit.listener.FilterEventListener;
import com.xkmxz.prismod.api.client.query.request.FilterQuery;
import com.xkmxz.prismod.api.client.submit.request.FilterRequest;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.client.application.handle.FilterOverride;
import com.xkmxz.prismod.client.application.handle.FilterRegistration;
import com.xkmxz.prismod.client.filter.FilterKey;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** 单一客户端执行队列；可注入调度器验证排队、回调和资源生命周期。 */
final class ClientFilterDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Executor executor;
    private final FilterBackend backend;
    private final ArrayDeque<Work> queue = new ArrayDeque<>();
    private final Map<UUID, Entry> resources = new LinkedHashMap<>();
    private final List<FilterEvent> pendingEvents = new ArrayList<>();
    private boolean scheduled;
    private boolean running;
    private volatile ReadView published;

    ClientFilterDispatcher(Executor executor, FilterBackend backend) {
        this.executor = Objects.requireNonNull(executor);
        this.backend = Objects.requireNonNull(backend);
        published = new ReadView(backend.snapshot(), List.copyOf(backend.filters()), Map.of());
        backend.observe(this::backendEvent);
    }

    CompletionStage<FilterReceipt> submit(FilterRequest request) {
        CompletableFuture<FilterReceipt> future = new CompletableFuture<>();
        enqueue(new Work(UUID.randomUUID(), request, future, null));
        // 调用方取消或手工完成派生 future，不得改变已提交的业务。
        return future.minimalCompletionStage();
    }

    @SuppressWarnings("unchecked")
    <R> R query(FilterQuery<R> request) {
        Objects.requireNonNull(request, "request");
        ReadView view = published;
        Object result;
        if (request instanceof FilterQuery.Snapshot) result = view.snapshot();
        else if (request instanceof FilterQuery.Filters q) {
            result = q.availableOnly()
                    ? view.filters().stream().filter(FilterDescriptor::available).toList() : view.filters();
        } else if (request instanceof FilterQuery.Resource q) {
            result = Optional.ofNullable(view.resources().get(Objects.requireNonNull(q.resourceId(), "resourceId")));
        } else if (request instanceof FilterQuery.Resources q) {
            String owner = requireOwner(q.ownerId());
            result = view.resources().values().stream().filter(r -> owner.equals(r.ownerId())).toList();
        } else if (request instanceof FilterQuery.SessionResources q) {
            UUID session = Objects.requireNonNull(q.sessionId(), "sessionId");
            result = view.resources().values().stream().filter(r -> session.equals(r.sessionId())).toList();
        } else throw new IllegalArgumentException("unsupported query");
        return (R) result;
    }

    private void enqueue(Work work) {
        synchronized (queue) {
            queue.add(work);
            if (scheduled) return;
            scheduled = true;
        }
        try {
            executor.execute(this::drain);
        } catch (RuntimeException error) {
            LOGGER.warn("Prismod client API scheduling failed", error);
            synchronized (queue) {
                Work rejected;
                while ((rejected = queue.poll()) != null) {
                    if (rejected.future() != null) rejected.future().complete(failure(rejected.id(),
                            FilterOperationStatus.UNAVAILABLE, FeedbackCode.UNAVAILABLE,
                            FilterFailureReason.UNKNOWN, "client scheduling unavailable"));
                }
                scheduled = false;
            }
        }
    }

    private void backendEvent(FilterEvent event) {
        if (running) pendingEvents.add(event);
        else enqueue(new Work(null, null, null, event));
    }

    private void drain() {
        while (true) {
            Work work;
            synchronized (queue) {
                work = queue.poll();
                if (work == null) { scheduled = false; return; }
            }
            running = true;
            FilterReceipt receipt = null;
            try {
                prune();
                if (work.event() != null) pendingEvents.add(work.event());
                else receipt = execute(work.id(), work.request());
                publish();
            } catch (RuntimeException error) {
                LOGGER.warn("Prismod client API execution failed", error);
                if (work.future() != null) receipt = failure(work.id(), FilterOperationStatus.FAILED,
                        FeedbackCode.FAILED, FilterFailureReason.UNKNOWN, "client operation failed");
            }
            if (receipt != null) {
                pendingEvents.add(work.request() instanceof FilterRequest.Batch
                        ? FilterEvent.batchOperationCompleted(receipt) : FilterEvent.operationCompleted(receipt));
            }
            List<FilterEvent> events = List.copyOf(pendingEvents);
            pendingEvents.clear();
            for (FilterEvent event : events) deliver(event);
            if (work.future() != null) work.future().complete(receipt);
            running = false;
        }
    }

    private FilterReceipt execute(UUID id, FilterRequest request) {
        try {
            return apply(id, Objects.requireNonNull(request, "request"));
        } catch (MissingSession error) {
            return failure(id, FilterOperationStatus.CLOSED, FeedbackCode.ALREADY_CLOSED,
                    FilterFailureReason.INVALID_ARGUMENT, "session is closed or unknown");
        } catch (IllegalArgumentException | NullPointerException error) {
            return failure(id, FilterOperationStatus.INVALID_ARGUMENT, FeedbackCode.INVALID_ARGUMENT,
                    FilterFailureReason.INVALID_ARGUMENT, error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.warn("Prismod client request failed", error);
            return failure(id, FilterOperationStatus.FAILED, FeedbackCode.FAILED,
                    FilterFailureReason.UNKNOWN, "client operation failed");
        }
    }

    private FilterReceipt apply(UUID id, FilterRequest request) {
        if (request instanceof FilterRequest.Register r) {
            String owner = requireOwner(r.ownerId());
            checkSession(r.sessionId(), owner);
            Objects.requireNonNull(r.postEffect(), "postEffect");
            var logical = r.logicalId() == null ? FilterKey.fromPostEffect(r.postEffect()).id() : r.logicalId();
            FilterRegistration handle = backend.register(owner, logical, r.postEffect(), r.metadata());
            Entry entry = new Entry(FilterResourceView.Kind.REGISTRATION, owner, r.sessionId(), logical, 0, handle);
            resources.put(entry.id, entry);
            if (handle.state() == FilterRegistrationState.FAILED) {
                return new FilterReceipt(id, FilterOperationStatus.FAILED,
                        handle.failureReason() == FilterFailureReason.ID_CONFLICT
                                ? FeedbackCode.INVALID_ARGUMENT : FeedbackCode.FAILED,
                        handle.failureReason(), handle.failureDetail(), 0, List.of(entry.id), List.of());
            }
            return success(id, 1, List.of(entry.id));
        }
        if (request instanceof FilterRequest.Override r) {
            String owner = requireOwner(r.ownerId());
            checkSession(r.sessionId(), owner);
            Objects.requireNonNull(r.filter(), "filter");
            FilterOverride handle = backend.override(owner, r.filter(),
                    FilterSnapshot.normalizeStrength(r.strength()), r.priority());
            Entry entry = new Entry(FilterResourceView.Kind.OVERRIDE, owner, r.sessionId(),
                    r.filter(), r.priority(), handle);
            resources.put(entry.id, entry);
            return success(id, 1, List.of(entry.id));
        }
        if (request instanceof FilterRequest.Release r) {
            return success(id, release(Objects.requireNonNull(r.resourceId(), "resourceId")), List.of());
        }
        if (request instanceof FilterRequest.ClearRegistrations r) {
            String owner = requireOwner(r.ownerId());
            int count = backend.clearRegistrations(owner);
            return success(id, count + clearOwner(owner, FilterResourceView.Kind.REGISTRATION), List.of());
        }
        if (request instanceof FilterRequest.ClearOverrides r) {
            String owner = requireOwner(r.ownerId());
            int count = backend.clearOverrides(owner);
            return success(id, count + clearOwner(owner, FilterResourceView.Kind.OVERRIDE), List.of());
        }
        if (request instanceof FilterRequest.Select r) {
            Objects.requireNonNull(r.filter(), "filter");
            long before = backend.snapshot().generation();
            backend.select(r.filter(), FilterSnapshot.normalizeStrength(r.strength()));
            return success(id, changed(before), List.of());
        }
        if (request instanceof FilterRequest.Force r) {
            Objects.requireNonNull(r.filter(), "filter");
            long before = backend.snapshot().generation();
            backend.force(r.filter(), FilterSnapshot.normalizeStrength(r.strength()));
            return success(id, changed(before), List.of());
        }
        if (request instanceof FilterRequest.ClearSelection) {
            long before = backend.snapshot().generation();
            backend.clearSelection();
            return success(id, changed(before), List.of());
        }
        if (request instanceof FilterRequest.ClearForced) {
            long before = backend.snapshot().generation();
            backend.clearForced();
            return success(id, changed(before), List.of());
        }
        if (request instanceof FilterRequest.OpenSession r) {
            Entry entry = new Entry(FilterResourceView.Kind.SESSION, requireOwner(r.ownerId()), null, null, 0, null);
            resources.put(entry.id, entry);
            return success(id, 1, List.of(entry.id));
        }
        if (request instanceof FilterRequest.ClearSession r) {
            requireSession(r.sessionId());
            return success(id, clearSession(r.sessionId()), List.of());
        }
        if (request instanceof FilterRequest.CloseSession r) {
            UUID session = Objects.requireNonNull(r.sessionId(), "sessionId");
            Entry entry = resources.get(session);
            if (entry != null && entry.kind != FilterResourceView.Kind.SESSION) {
                throw new IllegalArgumentException("resource is not a session");
            }
            return success(id, release(session), List.of());
        }
        if (request instanceof FilterRequest.SubscribeState r) {
            Entry entry = new Entry(FilterResourceView.Kind.SUBSCRIPTION, requireOwner(r.ownerId()), null, null, 0, null);
            entry.stateListener = Objects.requireNonNull(r.listener(), "listener");
            resources.put(entry.id, entry);
            return success(id, 1, List.of(entry.id));
        }
        if (request instanceof FilterRequest.SubscribeEvents r) {
            Entry entry = new Entry(FilterResourceView.Kind.SUBSCRIPTION, requireOwner(r.ownerId()), null, null, 0, null);
            entry.eventListener = Objects.requireNonNull(r.listener(), "listener");
            resources.put(entry.id, entry);
            return success(id, 1, List.of(entry.id));
        }
        if (request instanceof FilterRequest.Batch r) return batch(id, r);
        throw new IllegalArgumentException("unsupported request");
    }

    private FilterReceipt batch(UUID id, FilterRequest.Batch batch) {
        Entry session = requireSession(batch.sessionId());
        Objects.requireNonNull(batch.requests(), "requests");
        List<FilterReceipt> items = new ArrayList<>();
        for (FilterRequest item : batch.requests()) {
            UUID childId = UUID.randomUUID();
            if (item instanceof FilterRequest.Register r
                    && (r.sessionId() == null || r.sessionId().equals(session.id))) {
                items.add(execute(childId, new FilterRequest.Register(r.ownerId(), r.logicalId(),
                        r.postEffect(), r.metadata(), session.id)));
            } else if (item instanceof FilterRequest.Override r
                    && (r.sessionId() == null || r.sessionId().equals(session.id))) {
                items.add(execute(childId, new FilterRequest.Override(r.ownerId(), r.filter(),
                        r.strength(), r.priority(), session.id)));
            } else items.add(failure(childId, FilterOperationStatus.INVALID_ARGUMENT,
                    FeedbackCode.INVALID_ARGUMENT, FilterFailureReason.INVALID_ARGUMENT,
                    "batch accepts only registrations and overrides in its session"));
        }
        long successful = items.stream().filter(r -> r.status() == FilterOperationStatus.SUCCESS).count();
        FilterOperationStatus status = successful == items.size() ? FilterOperationStatus.SUCCESS
                : successful == 0 ? FilterOperationStatus.FAILED : FilterOperationStatus.PARTIAL;
        return new FilterReceipt(id, status,
                status == FilterOperationStatus.SUCCESS ? FeedbackCode.OK : FeedbackCode.FAILED,
                status == FilterOperationStatus.SUCCESS ? FilterFailureReason.NONE : FilterFailureReason.UNKNOWN,
                status == FilterOperationStatus.SUCCESS ? "" : "one or more batch items failed",
                items.stream().mapToInt(FilterReceipt::affectedCount).sum(),
                items.stream().flatMap(r -> r.resourceIds().stream()).toList(), items);
    }

    private int changed(long before) { return backend.snapshot().generation() == before ? 0 : 1; }

    private Entry requireSession(UUID session) {
        Objects.requireNonNull(session, "sessionId");
        Entry entry = resources.get(session);
        if (entry == null) throw new MissingSession();
        if (entry.kind != FilterResourceView.Kind.SESSION) throw new IllegalArgumentException("resource is not a session");
        return entry;
    }

    private void checkSession(UUID session, String owner) {
        if (session != null && !requireSession(session).owner.equals(owner)) {
            throw new IllegalArgumentException("session owner does not match request owner");
        }
    }

    private int clearSession(UUID session) {
        int count = 0;
        for (Entry entry : new ArrayList<>(resources.values())) {
            if (session.equals(entry.session)) count += release(entry.id);
        }
        return count;
    }

    private int clearOwner(String owner, FilterResourceView.Kind kind) {
        int count = 0;
        for (Entry entry : new ArrayList<>(resources.values())) {
            if (entry.owner.equals(owner) && entry.kind == kind) count += release(entry.id);
        }
        return count;
    }

    private int release(UUID id) {
        Entry entry = resources.get(id);
        if (entry == null) return 0;
        int count = entry.kind == FilterResourceView.Kind.SESSION ? clearSession(id) : 0;
        boolean alive = entry.alive();
        if (entry.handle != null) {
            try { entry.handle.close(); }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new IllegalStateException(error); }
        }
        resources.remove(id);
        return count + (alive ? 1 : 0);
    }

    private void prune() { resources.values().removeIf(entry -> !entry.alive()); }

    private void publish() {
        prune();
        Map<UUID, FilterResourceView> views = new LinkedHashMap<>();
        resources.forEach((id, entry) -> views.put(id, entry.view()));
        published = new ReadView(backend.snapshot(), List.copyOf(backend.filters()),
                Collections.unmodifiableMap(views));
    }

    private void deliver(FilterEvent event) {
        for (Entry entry : new ArrayList<>(resources.values())) {
            try {
                if (entry.stateListener != null && event.type() == FilterEventType.SNAPSHOT_CHANGED) {
                    entry.stateListener.onChanged(event.snapshot());
                }
                if (entry.eventListener != null) entry.eventListener.onEvent(event);
            } catch (Throwable error) {
                LOGGER.warn("Prismod client API listener failed", error);
            }
        }
    }

    private static String requireOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        return owner.trim();
    }

    private static FilterReceipt success(UUID id, int affected, List<UUID> resources) {
        return new FilterReceipt(id, FilterOperationStatus.SUCCESS, FeedbackCode.OK,
                FilterFailureReason.NONE, "", affected, resources, List.of());
    }

    private static FilterReceipt failure(UUID id, FilterOperationStatus status, FeedbackCode code,
                                         FilterFailureReason reason, String detail) {
        return new FilterReceipt(id, status, code, reason, detail, 0, List.of(), List.of());
    }

    private record ReadView(FilterSnapshot snapshot, List<FilterDescriptor> filters,
                            Map<UUID, FilterResourceView> resources) { }
    private record Work(UUID id, FilterRequest request, CompletableFuture<FilterReceipt> future,
                        FilterEvent event) { }
    private static final class MissingSession extends RuntimeException { }

    private static final class Entry {
        private final UUID id = UUID.randomUUID();
        private final FilterResourceView.Kind kind;
        private final String owner;
        private final UUID session;
        private final net.minecraft.resources.ResourceLocation filter;
        private final int priority;
        private final AutoCloseable handle;
        private FilterStateListener stateListener;
        private FilterEventListener eventListener;

        Entry(FilterResourceView.Kind kind, String owner, UUID session,
              net.minecraft.resources.ResourceLocation filter, int priority, AutoCloseable handle) {
            this.kind = kind; this.owner = owner; this.session = session;
            this.filter = filter; this.priority = priority; this.handle = handle;
        }
        boolean alive() {
            if (handle instanceof FilterRegistration r) return r.state() != FilterRegistrationState.CLOSED;
            return !(handle instanceof FilterOverride r) || r.isActive();
        }
        FilterResourceView view() {
            FilterFailureReason reason = handle instanceof FilterRegistration r
                    ? r.failureReason() : FilterFailureReason.NONE;
            boolean failed = handle instanceof FilterRegistration r && r.state() == FilterRegistrationState.FAILED;
            return new FilterResourceView(id, kind, owner, session, filter, priority,
                    failed ? FilterResourceView.State.FAILED : FilterResourceView.State.ACTIVE,
                    reason, handle instanceof FilterRegistration r ? r.failureDetail() : "");
        }
    }
}
