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
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

/** Client-only API; writes are scheduled on the client thread and reads return immutable snapshots. */
public final class ClientFilterApplication {
    private ClientFilterApplication() {
    }

    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        if (postEffect == null) throw new NullPointerException("postEffect");
        return registerCustomFilter(FilterKey.fromPostEffect(postEffect).id(), ownerId, postEffect, metadata);
    }

    public static FilterRegistration registerCustomFilter(ResourceLocation logicalId, String ownerId,
                                                           ResourceLocation postEffect, CustomFilterMetadata metadata) {
        if (logicalId == null) throw new NullPointerException("logicalId");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        if (postEffect == null) throw new NullPointerException("postEffect");
        String normalizedOwner = ownerId.trim();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) return refreshAfterRegistration(
                FilterRegistry.get().register(logicalId, normalizedOwner, postEffect, metadata));
        DeferredRegistration deferred = new DeferredRegistration(logicalId, normalizedOwner);
        minecraft.execute(() -> deferred.attach(refreshAfterRegistration(
                FilterRegistry.get().register(logicalId, normalizedOwner, postEffect, metadata))));
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
        if (filter == null) throw new NullPointerException("filter");
        runOnClientThread(() -> FilterManager.get().setForced(filter, strength));
    }

    /** Applies a session-only selection without changing the user's local selection or configuration. */
    public static void setSessionSelection(ResourceLocation filter, float strength) {
        if (filter == null) throw new NullPointerException("filter");
        runOnClientThread(() -> FilterManager.get().selectSession(new FilterKey(filter), strength));
    }

    /** Clears the session-only selection and restores the user's local selection. */
    public static void clearSessionSelection() {
        runOnClientThread(FilterManager.get()::clearSessionSelection);
    }

    public static FilterOverride createOverride(String ownerId, ResourceLocation filter, float strength, int priority) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        if (filter == null) throw new NullPointerException("filter");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            return FilterManager.get().addOverride(ownerId, new FilterKey(filter), strength, priority);
        }
        DeferredOverride deferred = new DeferredOverride(ownerId.trim(), filter, priority);
        minecraft.execute(() -> deferred.attach(FilterManager.get().addOverride(
                deferred.ownerName(), new FilterKey(filter), strength, priority)));
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
        runOnClientThread(FilterManager.get()::clearForced);
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

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) action.run();
        else minecraft.execute(action);
    }

    private static String requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        return ownerId.trim();
    }

    private static FilterRegistration refreshAfterRegistration(FilterRegistration registration) {
        FilterManager.get().registryChanged();
        return new RefreshingRegistration(registration);
    }

    private static FilterOperation executeOperation(IntSupplier action) {
        PendingOperation operation = new PendingOperation();
        Runnable work = () -> {
            try {
                operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, action.getAsInt());
            } catch (RuntimeException exception) {
                operation.complete(FilterOperationStatus.FAILED, FeedbackCode.FAILED, 0);
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
        private volatile FilterOperationStatus status = FilterOperationStatus.QUEUED;
        private volatile FeedbackCode code = FeedbackCode.OK;
        private volatile int affectedCount;

        private void complete(FilterOperationStatus status, FeedbackCode code, int affectedCount) {
            this.status = status;
            this.code = code;
            this.affectedCount = Math.max(0, affectedCount);
        }

        @Override public UUID id() { return id; }
        @Override public FilterOperationStatus status() { return status; }
        @Override public FeedbackCode code() { return code; }
        @Override public int affectedCount() { return affectedCount; }
        @Override public boolean completed() { return status != FilterOperationStatus.QUEUED; }
    }

    private static final class RefreshingRegistration implements FilterRegistration {
        private final FilterRegistration delegate;

        private RefreshingRegistration(FilterRegistration delegate) { this.delegate = delegate; }

        @Override public ResourceLocation id() { return delegate.id(); }
        @Override public String ownerId() { return delegate.ownerId(); }
        @Override public com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState state() { return delegate.state(); }
        @Override public com.xkmxz.prismod.api.common.state.filter.FilterFailureReason failureReason() { return delegate.failureReason(); }
        @Override public String failureDetail() { return delegate.failureDetail(); }
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
        private volatile FilterOverride delegate;
        private volatile boolean closed;

        private DeferredOverride(String ownerId, ResourceLocation id, int priority) {
            this.ownerId = ownerId;
            this.id = id;
            this.priority = priority;
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
        @Override public void close() {
            closed = true;
            FilterOverride current = delegate;
            if (current != null) current.close();
        }
    }

    private static final class DeferredRegistration implements FilterRegistration {
        private final ResourceLocation id;
        private final String ownerId;
        private volatile FilterRegistration delegate;
        private volatile boolean closed;

        private DeferredRegistration(ResourceLocation id, String ownerId) {
            this.id = id;
            this.ownerId = ownerId;
        }

        private void attach(FilterRegistration registration) {
            delegate = registration;
            if (closed) registration.close();
        }

        @Override public ResourceLocation id() { return id; }
        @Override public String ownerId() { return ownerId; }
        @Override public com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState state() {
            FilterRegistration current = delegate;
            if (closed) return com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.CLOSED;
            return current == null ? com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState.PENDING : current.state();
        }
        @Override public com.xkmxz.prismod.api.common.state.filter.FilterFailureReason failureReason() {
            FilterRegistration current = delegate;
            return current == null ? com.xkmxz.prismod.api.common.state.filter.FilterFailureReason.NONE : current.failureReason();
        }
        @Override public String failureDetail() {
            FilterRegistration current = delegate;
            return current == null ? "" : current.failureDetail();
        }
        @Override public void close() {
            closed = true;
            FilterRegistration current = delegate;
            if (current != null) current.close();
        }
    }
}
