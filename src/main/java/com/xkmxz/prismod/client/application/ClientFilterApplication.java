package com.xkmxz.prismod.client.application;

import com.xkmxz.prismod.api.client.contract.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import com.xkmxz.prismod.api.client.contract.FilterSnapshot;
import com.xkmxz.prismod.api.client.contract.FilterStateListener;
import com.xkmxz.prismod.api.client.contract.FilterSubscription;
import com.xkmxz.prismod.client.filter.state.FilterManager;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.api.client.event.FilterEventListener;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.client.operation.FilterBatchOperation;
import com.xkmxz.prismod.api.client.FilterClientSession;
import com.xkmxz.prismod.api.client.contract.FilterBatchItem;
import com.xkmxz.prismod.api.client.contract.FilterBatchResult;
import com.xkmxz.prismod.api.client.contract.FilterRegistrationRequest;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/** Client-only API; writes are scheduled on the client thread and reads return immutable snapshots. */
public final class ClientFilterApplication {
    private ClientFilterApplication() {
    }

    public static FilterClientSession openSession(String ownerId) {
        return new Session(requireOwner(ownerId));
    }

    private static final class Session implements FilterClientSession {
        private final String ownerId;
        private final List<FilterRegistration> registrations = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<FilterOverride> overrides = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile boolean closed;

        private Session(String ownerId) { this.ownerId = ownerId; }
        @Override public String ownerId() { return ownerId; }
        @Override public boolean closed() { return closed; }

        @Override public FilterBatchResult<FilterRegistration> registerAll(List<FilterRegistrationRequest> requests) {
            List<FilterRegistrationRequest> safe = requests == null ? List.of() : new java.util.ArrayList<>(requests);
            List<FilterBatchItem<FilterRegistration>> items = new java.util.ArrayList<>();
            List<FilterRegistration> handles = new java.util.ArrayList<>();
            List<FilterOperation> operations = new java.util.ArrayList<>();
            if (closed) return closedBatch(items, handles, operations);
            for (int i = 0; i < safe.size(); i++) {
                FilterRegistrationRequest request = safe.get(i);
                if (request == null) {
                    FilterOperation op = FilterOperation.failedOperation(FilterOperationStatus.INVALID_ARGUMENT,
                            FeedbackCode.INVALID_ARGUMENT, FilterFailureReason.INVALID_ARGUMENT, "request is null");
                    operations.add(op); items.add(new FilterBatchItem<>(i, null, op)); continue;
                }
                ResourceLocation logicalId = request.logicalId() == null
                        ? FilterKey.fromPostEffect(request.postEffect()).id() : request.logicalId();
                FilterRegistration registration = registerCustomFilter(logicalId, ownerId,
                        request.postEffect(), request.metadata());
                FilterOperation op = registration.operation();
                operations.add(op); items.add(new FilterBatchItem<>(i, registration, op));
                handles.add(registration); registrations.add(registration);
            }
            return finishBatch(items, handles, operations);
        }

        @Override public FilterBatchResult<FilterOverride> createOverrides(List<FilterOverrideRequest> requests) {
            List<FilterOverrideRequest> safe = requests == null ? List.of() : new java.util.ArrayList<>(requests);
            List<FilterBatchItem<FilterOverride>> items = new java.util.ArrayList<>();
            List<FilterOverride> handles = new java.util.ArrayList<>();
            List<FilterOperation> operations = new java.util.ArrayList<>();
            if (closed) return closedBatch(items, handles, operations);
            for (int i = 0; i < safe.size(); i++) {
                FilterOverrideRequest request = safe.get(i);
                if (request == null) {
                    FilterOperation op = FilterOperation.failedOperation(FilterOperationStatus.INVALID_ARGUMENT,
                            FeedbackCode.INVALID_ARGUMENT, FilterFailureReason.INVALID_ARGUMENT, "request is null");
                    operations.add(op); items.add(new FilterBatchItem<>(i, null, op)); continue;
                }
                FilterOverride override = createOverride(ownerId, request.filter(), request.strength(), request.priority());
                FilterOperation op = override.operation();
                operations.add(op); items.add(new FilterBatchItem<>(i, override, op));
                handles.add(override); overrides.add(override);
            }
            return finishBatch(items, handles, operations);
        }

        private <T> FilterBatchResult<T> finishBatch(List<FilterBatchItem<T>> items, List<T> handles,
                                                     List<FilterOperation> operations) {
            FilterOperationStatus status = aggregateStatus(operations);
            int success = (int) operations.stream().filter(o -> o.status() == FilterOperationStatus.SUCCESS).count();
            int failed = operations.size() - success;
            int affected = operations.stream().mapToInt(FilterOperation::affectedCount).sum();
            FilterBatchOperation batch = new FilterBatchOperation(UUID.randomUUID(), operations,
                    status == FilterOperationStatus.QUEUED ? null : status,
                    failed == 0 ? FeedbackCode.OK : FeedbackCode.FAILED, affected,
                    success, failed, failed == 0 ? FilterFailureReason.NONE : FilterFailureReason.UNKNOWN,
                    failed == 0 ? "" : "one or more batch items failed");
            if (status == FilterOperationStatus.QUEUED) {
                Minecraft.getInstance().execute(() -> completeBatch(batch));
            } else {
                FilterManager.get().batchOperationCompleted(batch);
            }
            return new FilterBatchResult<>(handles, batch, items);
        }

        private static void completeBatch(FilterBatchOperation batch) {
            List<FilterOperation> operations = batch.items();
            int success = (int) operations.stream().filter(o -> o.status() == FilterOperationStatus.SUCCESS).count();
            int failed = (int) operations.stream().filter(o -> o.completed() && o.status() != FilterOperationStatus.SUCCESS).count();
            int affected = operations.stream().mapToInt(FilterOperation::affectedCount).sum();
            FilterOperationStatus status = success == operations.size() ? FilterOperationStatus.SUCCESS
                    : success == 0 ? FilterOperationStatus.FAILED : FilterOperationStatus.PARTIAL;
            batch.complete(status, failed == 0 ? FeedbackCode.OK : FeedbackCode.FAILED, affected,
                    success, failed, failed == 0 ? FilterFailureReason.NONE : FilterFailureReason.UNKNOWN,
                    failed == 0 ? "" : "one or more batch items failed");
            FilterManager.get().batchOperationCompleted(batch);
        }

        private <T> FilterBatchResult<T> closedBatch(List<FilterBatchItem<T>> items, List<T> handles,
                                                     List<FilterOperation> operations) {
            FilterBatchOperation batch = new FilterBatchOperation(UUID.randomUUID(), operations,
                    FilterOperationStatus.CLOSED, FeedbackCode.ALREADY_CLOSED, 0, 0, 0,
                    FilterFailureReason.INVALID_ARGUMENT, "session is closed");
            FilterManager.get().batchOperationCompleted(batch);
            return new FilterBatchResult<>(handles, batch, items);
        }

        private static FilterOperationStatus aggregateStatus(List<FilterOperation> operations) {
            if (operations.isEmpty()) return FilterOperationStatus.SUCCESS;
            if (operations.stream().anyMatch(o -> o.status() == FilterOperationStatus.QUEUED)) {
                return FilterOperationStatus.QUEUED;
            }
            long successes = operations.stream().filter(o -> o.status() == FilterOperationStatus.SUCCESS).count();
            if (successes == operations.size()) return FilterOperationStatus.SUCCESS;
            return successes == 0 ? FilterOperationStatus.FAILED : FilterOperationStatus.PARTIAL;
        }

        @Override public FilterOperation clear() {
            return executeOperation(() -> {
                int count = 0;
                for (FilterRegistration registration : registrations) {
                    if (registration.state() != com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.CLOSED) {
                        registration.close();
                        count++;
                    }
                }
                for (FilterOverride override : overrides) {
                    if (override.isActive()) {
                        override.close();
                        count++;
                    }
                }
                registrations.clear();
                overrides.clear();
                return count;
            });
        }

        @Override public void close() {
            if (!closed) {
                closed = true;
                clearRegistrationsOperation(ownerId);
                for (FilterOverride override : overrides) override.close();
                registrations.clear();
                overrides.clear();
            }
        }
    }

    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        return registerCustomFilterOperation(ownerId, postEffect, metadata);
    }

    @Deprecated
    public static FilterRegistration registerCustomFilterOperation(String ownerId, ResourceLocation postEffect,
                                                                    CustomFilterMetadata metadata) {
        if (postEffect == null) throw new NullPointerException("postEffect");
        return registerCustomFilterOperation(FilterKey.fromPostEffect(postEffect).id(), ownerId, postEffect, metadata);
    }

    public static FilterRegistration registerCustomFilter(ResourceLocation logicalId, String ownerId,
                                                           ResourceLocation postEffect, CustomFilterMetadata metadata) {
        return registerCustomFilterOperation(logicalId, ownerId, postEffect, metadata);
    }

    @Deprecated
    public static FilterRegistration registerCustomFilterOperation(ResourceLocation logicalId, String ownerId,
                                                                    ResourceLocation postEffect, CustomFilterMetadata metadata) {
        if (logicalId == null) throw new NullPointerException("logicalId");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        if (postEffect == null) throw new NullPointerException("postEffect");
        String normalizedOwner = ownerId.trim();
        PendingOperation operation = new PendingOperation();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            FilterRegistration registration = refreshAfterRegistration(
                    FilterRegistry.get().register(logicalId, normalizedOwner, postEffect, metadata));
            completeRegistrationOperation(operation, registration);
            FilterManager.get().operationCompleted(operation);
            return new RefreshingRegistration(registration, operation);
        }
        DeferredRegistration deferred = new DeferredRegistration(logicalId, normalizedOwner, operation);
        minecraft.execute(() -> {
            try {
                FilterRegistration registration = refreshAfterRegistration(
                        FilterRegistry.get().register(logicalId, normalizedOwner, postEffect, metadata));
                deferred.attach(registration);
                completeRegistrationOperation(operation, registration);
            } catch (RuntimeException exception) {
                deferred.fail(FilterFailureReason.UNKNOWN, "registration failed");
                operation.complete(FilterOperationStatus.FAILED, FeedbackCode.FAILED,
                        FilterFailureReason.UNKNOWN, "registration failed", 0);
            }
            FilterManager.get().operationCompleted(operation);
        });
        return deferred;
    }

    public static FilterOperation clearRegistrationsOperation(String ownerId) {
        String normalizedOwner = requireOwner(ownerId);
        return executeOperation(() -> {
            int count = FilterRegistry.get().unregisterOwner(normalizedOwner);
            if (count > 0) FilterManager.get().registryChanged();
            return count;
        });
    }

    /** Sets one logical filter ID as the forced filter. */
    public static void setForcedFilter(ResourceLocation filter, float strength) {
        setForcedFilterOperation(filter, strength);
    }

    public static FilterOperation setForcedFilterOperation(ResourceLocation filter, float strength) {
        if (filter == null) throw new NullPointerException("filter");
        return executeOperation(() -> applyState(() -> FilterManager.get().setForced(filter, strength)));
    }

    /** Applies a session-only selection without changing the user's local selection or configuration. */
    public static void setSessionSelection(ResourceLocation filter, float strength) {
        setSessionSelectionOperation(filter, strength);
    }

    public static FilterOperation setSessionSelectionOperation(ResourceLocation filter, float strength) {
        if (filter == null) throw new NullPointerException("filter");
        return executeOperation(() -> applyState(() -> FilterManager.get().selectSession(new FilterKey(filter), strength)));
    }

    /** Clears the session-only selection and restores the user's local selection. */
    public static void clearSessionSelection() {
        clearSessionSelectionOperation();
    }

    public static FilterOperation clearSessionSelectionOperation() {
        return executeOperation(() -> applyState(FilterManager.get()::clearSessionSelection));
    }

    public static FilterOverride createOverride(String ownerId, ResourceLocation filter, float strength, int priority) {
        return createOverrideOperation(ownerId, filter, strength, priority);
    }

    @Deprecated
    public static FilterOverride createOverrideOperation(String ownerId, ResourceLocation filter, float strength, int priority) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        if (filter == null) throw new NullPointerException("filter");
        PendingOperation operation = new PendingOperation();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            FilterOverride override = FilterManager.get().addOverride(ownerId, new FilterKey(filter), strength, priority);
            operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, 1);
            FilterManager.get().operationCompleted(operation);
            return new OperationOverride(override, operation);
        }
        DeferredOverride deferred = new DeferredOverride(ownerId.trim(), filter, priority, operation);
        minecraft.execute(() -> {
            try {
                deferred.attach(FilterManager.get().addOverride(
                        deferred.ownerName(), new FilterKey(filter), strength, priority));
                operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, 1);
            } catch (RuntimeException exception) {
                deferred.close();
                operation.complete(FilterOperationStatus.FAILED, FeedbackCode.FAILED,
                        FilterFailureReason.UNKNOWN, "override creation failed", 0);
            }
            FilterManager.get().operationCompleted(operation);
        });
        return deferred;
    }

    public static FilterOperation clearOverridesOperation(String ownerId) {
        String normalizedOwner = requireOwner(ownerId);
        return executeOperation(() -> FilterManager.get().removeOverridesByOwner(normalizedOwner));
    }

    public static FilterSubscription subscribe(FilterStateListener listener) {
        return FilterManager.get().subscribe(listener);
    }

    public static FilterSubscription subscribeEvents(FilterEventListener listener) {
        return FilterManager.get().subscribeEvents(listener);
    }

    public static FilterSnapshot getSnapshot() {
        return FilterManager.get().snapshot();
    }

    /** Clears the forced filter and restores the current user selection. */
    public static void clearForcedFilter() {
        clearForcedFilterOperation();
    }

    public static FilterOperation clearForcedFilterOperation() {
        return executeOperation(() -> applyState(FilterManager.get()::clearForced));
    }

    /** Returns the final snapshot currently used by the renderer. */
    public static FilterSnapshot getEffectiveFilter() {
        return getSnapshot();
    }

    /** Returns the user's selected snapshot, even when rendering currently falls back to original. */
    public static FilterSnapshot getSelectedFilter() {
        FilterManager manager = FilterManager.get();
        FilterSnapshot current = manager.snapshot();
        return new FilterSnapshot(current.selectedFilter(), current.selectedStrength(), false,
                current.renderAvailable(), current.selectedFilter(), current.selectedStrength(),
                "", 0, current.fallbackReason(), current.generation());
    }

    /**
     * Returns a fresh immutable snapshot of the filters currently known to Prismod.
     * The registry is read at call time, so resource reloads and registrations are visible immediately.
     */
    public static List<FilterDescriptor> getFilters() {
        return FilterRegistry.get().descriptors();
    }

    /** Alias that makes it explicit that the result is the current runtime list. */
    public static List<FilterDescriptor> getAvailableFilters() {
        return getFilters().stream().filter(FilterDescriptor::available).toList();
    }

    private static String requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        return ownerId.trim();
    }

    private static FilterRegistration refreshAfterRegistration(FilterRegistration registration) {
        FilterManager.get().registryChanged();
        return registration;
    }

    private static void completeRegistrationOperation(PendingOperation operation, FilterRegistration registration) {
        if (registration.state() == com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.FAILED) {
            FeedbackCode code = registration.failureReason() == FilterFailureReason.ID_CONFLICT
                    ? FeedbackCode.INVALID_ARGUMENT : FeedbackCode.FAILED;
            operation.complete(FilterOperationStatus.FAILED, code, registration.failureReason(),
                    registration.failureDetail(), 0);
        } else {
            operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, 1);
        }
    }

    private static int applyState(Runnable action) {
        long before = FilterManager.get().snapshot().generation();
        action.run();
        return FilterManager.get().snapshot().generation() == before ? 0 : 1;
    }

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) action.run();
        else minecraft.execute(action);
    }

    private static FilterOperation executeOperation(IntSupplier action) {
        PendingOperation operation = new PendingOperation();
        Runnable work = () -> {
            try {
                operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, action.getAsInt());
            } catch (RuntimeException exception) {
                operation.complete(FilterOperationStatus.FAILED, FeedbackCode.FAILED,
                        FilterFailureReason.UNKNOWN, "client operation failed", 0);
            }
            FilterManager.get().operationCompleted(operation);
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) work.run();
        else minecraft.execute(work);
        return operation;
    }

    private static final class PendingOperation implements FilterOperation {
        private final UUID id = UUID.randomUUID();
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile FilterOperationStatus status = FilterOperationStatus.QUEUED;
        private volatile FeedbackCode code = FeedbackCode.OK;
        private volatile int affectedCount;
        private volatile FilterFailureReason failureReason = FilterFailureReason.NONE;
        private volatile String failureDetail = "";

        private void complete(FilterOperationStatus status, FeedbackCode code, int affectedCount) {
            complete(status, code, FilterFailureReason.NONE, "", affectedCount);
        }

        private synchronized void complete(FilterOperationStatus status, FeedbackCode code,
                                            FilterFailureReason failureReason, String failureDetail,
                                            int affectedCount) {
            if (completed.get()) return;
            this.status = status == null ? FilterOperationStatus.FAILED : status;
            this.code = code == null ? FeedbackCode.FAILED : code;
            this.failureReason = failureReason == null ? FilterFailureReason.UNKNOWN : failureReason;
            this.failureDetail = failureDetail == null ? "" : failureDetail;
            this.affectedCount = Math.max(0, affectedCount);
            completed.set(true);
        }

        @Override public UUID id() { return id; }
        @Override public FilterOperationStatus status() { return status; }
        @Override public FeedbackCode code() { return code; }
        @Override public int affectedCount() { return affectedCount; }
        @Override public boolean completed() { return completed.get(); }
        @Override public FilterFailureReason failureReason() { return failureReason; }
        @Override public String failureDetail() { return failureDetail; }
    }

    private static final class RefreshingRegistration implements FilterRegistration {
        private final FilterRegistration delegate;
        private final FilterOperation operation;

        private RefreshingRegistration(FilterRegistration delegate, FilterOperation operation) {
            this.delegate = delegate;
            this.operation = operation;
        }

        @Override public ResourceLocation id() { return delegate.id(); }
        @Override public String ownerId() { return delegate.ownerId(); }
        @Override public com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState state() { return delegate.state(); }
        @Override public com.xkmxz.prismod.api.common.state.filter.FilterFailureReason failureReason() { return delegate.failureReason(); }
        @Override public String failureDetail() { return delegate.failureDetail(); }
        @Override public FilterOperation operation() { return operation; }
        @Override public void close() {
            runOnClientThread(() -> {
                delegate.close();
                FilterManager.get().registryChanged();
            });
        }
    }

    private static final class DeferredOverride implements FilterOverride {
        private final String ownerId;
        private final ResourceLocation id;
        private final int priority;
        private final FilterOperation operation;
        private volatile FilterOverride delegate;
        private volatile boolean closed;

        private DeferredOverride(String ownerId, ResourceLocation id, int priority, FilterOperation operation) {
            this.ownerId = ownerId;
            this.id = id;
            this.priority = priority;
            this.operation = operation;
        }

        private String ownerName() { return ownerId; }

        private void attach(FilterOverride delegate) {
            this.delegate = delegate;
            if (closed) delegate.close();
        }

        @Override public ResourceLocation id() { return id; }
        @Override public String ownerId() { return ownerId; }
        @Override public int priority() { return priority; }
        @Override public com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState state() {
            FilterOverride current = delegate;
            if (closed) return com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState.CLOSED;
            return current == null ? com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState.PENDING : current.state();
        }
        @Override public boolean isActive() { return state() == com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState.ACTIVE; }
        @Override public FilterOperation operation() { return operation; }
        @Override public void close() {
            closed = true;
            FilterOverride current = delegate;
            if (current != null) current.close();
        }
    }

    private static final class DeferredRegistration implements FilterRegistration {
        private final ResourceLocation id;
        private final String ownerId;
        private final FilterOperation operation;
        private volatile FilterRegistration delegate;
        private volatile boolean closed;
        private volatile boolean failed;

        private DeferredRegistration(ResourceLocation id, String ownerId, FilterOperation operation) {
            this.id = id;
            this.ownerId = ownerId;
            this.operation = operation;
        }

        private void attach(FilterRegistration registration) {
            delegate = registration;
            if (closed) registration.close();
        }

        private volatile FilterFailureReason failureReason = FilterFailureReason.NONE;
        private volatile String failureDetail = "";

        private void fail(FilterFailureReason reason, String detail) {
            failed = true;
            failureReason = reason == null ? FilterFailureReason.UNKNOWN : reason;
            failureDetail = detail == null ? "" : detail;
        }

        @Override public ResourceLocation id() { return id; }
        @Override public String ownerId() { return ownerId; }
        @Override public com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState state() {
            FilterRegistration current = delegate;
            if (closed) return com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.CLOSED;
            if (failed) return com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.FAILED;
            return current == null ? com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.PENDING : current.state();
        }
        @Override public com.xkmxz.prismod.api.common.state.filter.FilterFailureReason failureReason() {
            FilterRegistration current = delegate;
            return current == null ? failureReason : current.failureReason();
        }
        @Override public String failureDetail() {
            FilterRegistration current = delegate;
            return current == null ? failureDetail : current.failureDetail();
        }
        @Override public FilterOperation operation() { return operation; }
        @Override public void close() {
            closed = true;
            FilterRegistration current = delegate;
            if (current != null) current.close();
        }
    }

    private static final class OperationOverride implements FilterOverride {
        private final FilterOverride delegate;
        private final FilterOperation operation;

        private OperationOverride(FilterOverride delegate, FilterOperation operation) {
            this.delegate = delegate;
            this.operation = operation;
        }

        @Override public ResourceLocation id() { return delegate.id(); }
        @Override public String ownerId() { return delegate.ownerId(); }
        @Override public int priority() { return delegate.priority(); }
        @Override public com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState state() { return delegate.state(); }
        @Override public boolean isActive() { return delegate.isActive(); }
        @Override public FilterOperation operation() { return operation; }
        @Override public void close() { delegate.close(); }
    }
}
