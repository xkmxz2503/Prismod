package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.client.filter.state.FilterManager;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Client-only API; writes are scheduled on the client thread and reads return immutable snapshots. */
public final class FilterApi {
    private FilterApi() {
    }

    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        return FilterRegistry.get().register(ownerId, postEffect, metadata);
    }

    public static FilterRegistration registerCustomFilter(ResourceLocation logicalId, String ownerId,
                                                           ResourceLocation postEffect, CustomFilterMetadata metadata) {
        return FilterRegistry.get().register(logicalId, ownerId, postEffect, metadata);
    }

    public static int clearRegistrations(String ownerId) {
        return FilterRegistry.get().unregisterOwner(ownerId);
    }

    /** Sets one logical filter ID as the forced filter. */
    public static void setForcedFilter(ResourceLocation filter, float strength) {
        runOnClientThread(() -> FilterManager.get().setForced(filter, strength));
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

    public static int clearOverrides(String ownerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) return FilterManager.get().removeOverridesByOwner(ownerId);
        minecraft.execute(() -> FilterManager.get().removeOverridesByOwner(ownerId));
        return 0;
    }

    public static FilterSubscription subscribe(FilterStateListener listener) {
        return FilterManager.get().subscribe(listener);
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

    public static List<FilterDescriptor> getFilters() {
        return FilterRegistry.get().descriptors();
    }

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) action.run();
        else minecraft.execute(action);
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
        @Override public com.xkmxz.prismod.api.common.state.FilterOverrideState state() {
            FilterOverride current = delegate;
            if (closed) return com.xkmxz.prismod.api.common.state.FilterOverrideState.CLOSED;
            return current == null ? com.xkmxz.prismod.api.common.state.FilterOverrideState.PENDING : current.state();
        }
        @Override public boolean isActive() { return state() == com.xkmxz.prismod.api.common.state.FilterOverrideState.ACTIVE; }
        @Override public void close() {
            closed = true;
            FilterOverride current = delegate;
            if (current != null) current.close();
        }
    }
}
