package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.client.filter.state.FilterManager;
import com.xkmxz.prismod.client.filter.state.FilterSelection;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

/** Client-only API; writes are scheduled on the client thread and reads return immutable snapshots. */
public final class FilterApi {
    private FilterApi() {
    }

    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        return FilterRegistry.get().register(ownerId, postEffect, metadata);
    }

    /** Sets one logical filter ID as the forced filter. */
    public static void setForcedFilter(ResourceLocation filter, float strength) {
        runOnClientThread(() -> FilterManager.get().setForced(filter, strength));
    }

    /** Clears the forced filter and restores the current user selection. */
    public static void clearForcedFilter() {
        runOnClientThread(FilterManager.get()::clearForced);
    }

    /** Returns the final snapshot currently used by the renderer. */
    public static FilterSnapshot getEffectiveFilter() {
        FilterManager manager = FilterManager.get();
        return snapshot(manager.effectiveSelection(), manager.isRenderAvailable());
    }

    /** Returns the user's selected snapshot, even when rendering currently falls back to original. */
    public static FilterSnapshot getSelectedFilter() {
        FilterManager manager = FilterManager.get();
        return snapshot(manager.selectedSelection(), manager.isRenderAvailable());
    }

    private static FilterSnapshot snapshot(FilterSelection selection, boolean renderAvailable) {
        return new FilterSnapshot(selection.key().id(), selection.strength(), selection.forced(), renderAvailable);
    }

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) action.run();
        else minecraft.execute(action);
    }
}
