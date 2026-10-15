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

/** Stable client facade. Runtime conversion and scheduling live in the application layer. */
public final class FilterClientApi {
    private FilterClientApi() {
    }

    public static FilterClientSession openSession(String ownerId) {
        return ClientFilterApplication.openSession(ownerId);
    }

    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilter(ownerId, postEffect, metadata);
    }

    @Deprecated
    public static FilterRegistration registerCustomFilterOperation(String ownerId, ResourceLocation postEffect,
                                                                    CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilterOperation(ownerId, postEffect, metadata);
    }

    public static FilterRegistration registerCustomFilter(ResourceLocation logicalId, String ownerId,
                                                           ResourceLocation postEffect, CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilter(logicalId, ownerId, postEffect, metadata);
    }

    @Deprecated
    public static FilterRegistration registerCustomFilterOperation(ResourceLocation logicalId, String ownerId,
                                                                    ResourceLocation postEffect, CustomFilterMetadata metadata) {
        return ClientFilterApplication.registerCustomFilterOperation(logicalId, ownerId, postEffect, metadata);
    }

    public static FilterOperation clearRegistrations(String ownerId) {
        return ClientFilterApplication.clearRegistrationsOperation(ownerId);
    }

    public static void setForcedFilter(ResourceLocation filter, float strength) {
        ClientFilterApplication.setForcedFilter(filter, strength);
    }

    public static FilterOperation setForcedFilterOperation(ResourceLocation filter, float strength) {
        return ClientFilterApplication.setForcedFilterOperation(filter, strength);
    }

    public static void setSessionSelection(ResourceLocation filter, float strength) {
        ClientFilterApplication.setSessionSelection(filter, strength);
    }

    public static FilterOperation setSessionSelectionOperation(ResourceLocation filter, float strength) {
        return ClientFilterApplication.setSessionSelectionOperation(filter, strength);
    }

    public static void clearSessionSelection() {
        ClientFilterApplication.clearSessionSelection();
    }

    public static FilterOperation clearSessionSelectionOperation() {
        return ClientFilterApplication.clearSessionSelectionOperation();
    }

    public static FilterOverride createOverride(String ownerId, ResourceLocation filter, float strength, int priority) {
        return ClientFilterApplication.createOverride(ownerId, filter, strength, priority);
    }

    @Deprecated
    public static FilterOverride createOverrideOperation(String ownerId, ResourceLocation filter, float strength, int priority) {
        return ClientFilterApplication.createOverrideOperation(ownerId, filter, strength, priority);
    }

    public static FilterOperation clearOverrides(String ownerId) {
        return ClientFilterApplication.clearOverridesOperation(ownerId);
    }

    public static FilterSubscription subscribe(FilterStateListener listener) {
        return ClientFilterApplication.subscribe(listener);
    }

    public static FilterSubscription subscribeEvents(FilterEventListener listener) {
        return ClientFilterApplication.subscribeEvents(listener);
    }

    public static FilterSnapshot snapshot() {
        return ClientFilterApplication.getSnapshot();
    }

    public static void clearForcedFilter() {
        ClientFilterApplication.clearForcedFilter();
    }

    public static FilterOperation clearForcedFilterOperation() {
        return ClientFilterApplication.clearForcedFilterOperation();
    }

    public static List<FilterDescriptor> filters() {
        return ClientFilterApplication.getFilters();
    }

    public static List<FilterDescriptor> availableFilters() {
        return ClientFilterApplication.getAvailableFilters();
    }
}
