package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import com.xkmxz.prismod.api.client.contract.FilterSnapshot;
import com.xkmxz.prismod.api.client.contract.FilterStateListener;
import com.xkmxz.prismod.api.client.contract.FilterSubscription;
import com.xkmxz.prismod.api.client.event.FilterEventListener;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import com.xkmxz.prismod.client.application.ClientFilterApplication;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Stable public client facade for Prismod integrations.
 *
 * <p>External client mods should use this class and the immutable contract types
 * returned by it. Runtime conversion, validation, and Minecraft client-thread
 * scheduling live in the application layer and are intentionally hidden here.</p>
 *
 * <p>Methods without an {@code Operation} suffix are compatibility-friendly
 * convenience methods for callers that do not need to inspect completion. The
 * {@code Operation}-suffixed methods expose queued execution and structured
 * failure information. Registration and override methods return lifecycle
 * handles that should be closed when the owning feature is disabled.</p>
 */
public final class FilterClientApi {
    private FilterClientApi() {
    }

    /**
     * Opens an owner-scoped lifecycle session for registrations and overrides.
     *
     * @param ownerId stable non-blank identifier of the calling mod or feature
     * @return a session whose {@link FilterClientSession#close()} releases only
     *         resources created through that session
     * @throws NullPointerException if {@code ownerId} is {@code null}
     * @throws IllegalArgumentException if {@code ownerId} is blank
     */
    public static FilterClientSession openSession(String ownerId) {
        return ClientFilterApplication.openSession(ownerId);
    }

    /**
     * Registers a custom PostChain filter and derives its logical ID from the
     * PostChain resource path.
     *
     * @param ownerId stable non-blank owner identifier
     * @param postEffect actual PostChain resource path, not a logical filter ID
     * @param metadata display metadata and default strength
     * @return registration lifecycle handle; inspect {@link FilterRegistration#operation()}
     *         when the call may be queued
     */
    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilter(ownerId, postEffect, metadata);
    }

    /**
     * Compatibility alias for callers that used the pre-v1 operation-named entry point.
     *
     * <p>New code should call {@link #registerCustomFilter(String, ResourceLocation,
     * CustomFilterMetadata)} and inspect the returned handle.</p>
     * Prefer {@link #registerCustomFilter(String, ResourceLocation, CustomFilterMetadata)}
     * and inspect {@link FilterRegistration#operation()} on the returned handle.
     */
    @Deprecated
    public static FilterRegistration registerCustomFilterOperation(String ownerId, ResourceLocation postEffect,
                                                                    CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilterOperation(ownerId, postEffect, metadata);
    }

    /**
     * Registers a custom PostChain filter with an explicit logical ID.
     *
     * @param logicalId stable {@code namespace:path} identity used by selection APIs
     * @param ownerId stable non-blank owner identifier
     * @param postEffect actual PostChain resource path
     * @param metadata display metadata and default strength
     * @return registration lifecycle handle
     */
    public static FilterRegistration registerCustomFilter(ResourceLocation logicalId, String ownerId,
                                                           ResourceLocation postEffect, CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilter(logicalId, ownerId, postEffect, metadata);
    }

    /**
     * Compatibility alias for the explicit-ID registration overload.
     * Prefer the overload without the {@code Operation} suffix.
     */
    @Deprecated
    public static FilterRegistration registerCustomFilterOperation(ResourceLocation logicalId, String ownerId,
                                                                    ResourceLocation postEffect, CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilterOperation(logicalId, ownerId, postEffect, metadata);
    }

    /**
     * Closes all registrations owned by {@code ownerId}.
     *
     * @param ownerId owner identifier to clear
     * @return observable operation describing the cleanup
     */
    public static FilterOperation clearRegistrations(String ownerId) {
        return ClientFilterApplication.clearRegistrationsOperation(ownerId);
    }

    /**
     * Sets a temporary forced filter. The effective render priority places a
     * valid forced filter above the player's ordinary selection.
     *
     * @param filter logical filter ID
     * @param strength requested strength, normalized to {@code [0, 1]}
     */
    public static void setForcedFilter(ResourceLocation filter, float strength) {
        ClientFilterApplication.setForcedFilter(filter, strength);
    }

    /**
     * Sets a forced filter and exposes queued or completed execution state.
     *
     * @param filter logical filter ID
     * @param strength requested strength, normalized to {@code [0, 1]}
     * @return operation that transitions from {@code QUEUED} to a terminal state
     */
    public static FilterOperation setForcedFilterOperation(ResourceLocation filter, float strength) {
        return ClientFilterApplication.setForcedFilterOperation(filter, strength);
    }

    /**
     * Sets a temporary session selection without changing persistent player configuration.
     *
     * @param filter logical filter ID
     * @param strength requested strength, normalized to {@code [0, 1]}
     */
    public static void setSessionSelection(ResourceLocation filter, float strength) {
        ClientFilterApplication.setSessionSelection(filter, strength);
    }

    /**
     * Sets a session selection and exposes queued or completed execution state.
     *
     * @param filter logical filter ID
     * @param strength requested strength, normalized to {@code [0, 1]}
     * @return operation that transitions from {@code QUEUED} to a terminal state
     */
    public static FilterOperation setSessionSelectionOperation(ResourceLocation filter, float strength) {
        return ClientFilterApplication.setSessionSelectionOperation(filter, strength);
    }

    /** Clears the temporary session selection and restores the ordinary selection path. */
    public static void clearSessionSelection() {
        ClientFilterApplication.clearSessionSelection();
    }

    /** Clears the session selection and exposes queued or completed execution state. */
    public static FilterOperation clearSessionSelectionOperation() {
        return ClientFilterApplication.clearSessionSelectionOperation();
    }

    /**
     * Creates an owner-scoped priority override.
     *
     * @param ownerId stable non-blank owner identifier
     * @param filter logical filter ID
     * @param strength requested strength, normalized to {@code [0, 1]}
     * @param priority larger values win; equal priorities favor the newer override
     * @return override lifecycle handle
     */
    public static FilterOverride createOverride(String ownerId, ResourceLocation filter, float strength, int priority) {
        return ClientFilterApplication.createOverride(ownerId, filter, strength, priority);
    }

    /**
     * Compatibility alias for override creation. Prefer the handle returned by
     * {@link #createOverride(String, ResourceLocation, float, int)}.
     */
    @Deprecated
    public static FilterOverride createOverrideOperation(String ownerId, ResourceLocation filter, float strength, int priority) {
        return ClientFilterApplication.createOverrideOperation(ownerId, filter, strength, priority);
    }

    /**
     * Removes all active overrides owned by {@code ownerId}.
     *
     * @param ownerId owner identifier to clear
     * @return observable operation describing the cleanup
     */
    public static FilterOperation clearOverrides(String ownerId) {
        return ClientFilterApplication.clearOverridesOperation(ownerId);
    }

    /**
     * Subscribes to immutable final-state snapshots.
     *
     * <p>No initial snapshot is replayed. Callbacks run synchronously on the
     * Minecraft client thread in state-change order.</p>
     *
     * @param listener listener to invoke for state changes
     * @return subscription lifecycle handle
     */
    public static FilterSubscription subscribe(FilterStateListener listener) {
        return ClientFilterApplication.subscribe(listener);
    }

    /**
     * Subscribes to typed registry, availability, snapshot, and operation events.
     *
     * @param listener listener to invoke for events
     * @return subscription lifecycle handle
     */
    public static FilterSubscription subscribeEvents(FilterEventListener listener) {
        return ClientFilterApplication.subscribeEvents(listener);
    }

    /**
     * Returns the latest immutable final render snapshot.
     *
     * <p>The effective filter may temporarily be {@code prismod:original} when
     * rendering is unavailable; selected-filter and fallback metadata remain
     * available for recovery and diagnostics.</p>
     *
     * @return latest published client snapshot
     */
    public static FilterSnapshot snapshot() {
        return ClientFilterApplication.getSnapshot();
    }

    /** Clears the forced filter and restores the lower-priority selection path. */
    public static void clearForcedFilter() {
        ClientFilterApplication.clearForcedFilter();
    }

    /** Clears the forced filter and exposes queued or completed execution state. */
    public static FilterOperation clearForcedFilterOperation() {
        return ClientFilterApplication.clearForcedFilterOperation();
    }

    /**
     * Returns immutable descriptions for all discovered and registered filters.
     *
     * @return immutable filter descriptor list
     */
    public static List<FilterDescriptor> filters() {
        return ClientFilterApplication.getFilters();
    }

    /**
     * Returns immutable descriptions for filters currently available to render.
     *
     * @return immutable available-filter descriptor list
     */
    public static List<FilterDescriptor> availableFilters() {
        return ClientFilterApplication.getAvailableFilters();
    }
}
