package com.xkmxz.prismod.api.client.contract;

import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.api.common.state.filter.FilterFallbackReason;

import java.util.Objects;

/** Immutable client-side view of one filter selection. */
public record FilterSnapshot(
        ResourceLocation filter,
        float strength,
        boolean forced,
        boolean renderAvailable,
        ResourceLocation selectedFilter,
        float selectedStrength,
        String overrideOwner,
        int overridePriority,
        FilterFallbackReason fallbackReason,
        long generation
) {
    public FilterSnapshot(ResourceLocation filter, float strength, boolean forced, boolean renderAvailable) {
        this(filter, strength, forced, renderAvailable, filter, strength, "", 0,
                FilterFallbackReason.NONE, 0L);
    }

    public FilterSnapshot {
        filter = Objects.requireNonNull(filter, "filter");
        strength = normalizeStrength(strength);
        selectedFilter = Objects.requireNonNull(selectedFilter, "selectedFilter");
        selectedStrength = normalizeStrength(selectedStrength);
        overrideOwner = overrideOwner == null ? "" : overrideOwner;
        fallbackReason = fallbackReason == null ? FilterFallbackReason.NONE : fallbackReason;
    }

    /** Non-finite values become zero; finite values are clamped to [0, 1]. */
    public static float normalizeStrength(double strength) {
        if (!Double.isFinite(strength)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, strength));
    }
}
