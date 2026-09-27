package com.xkmxz.prismod.api.common.model;

import com.xkmxz.prismod.api.common.state.FilterFallbackReason;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable state snapshot safe to retain or pass across threads. */
public record FilterSnapshot(
        ResourceLocation filter,
        float strength,
        ResourceLocation selectedFilter,
        float selectedStrength,
        String overrideOwner,
        int overridePriority,
        boolean renderAvailable,
        FilterFallbackReason fallbackReason,
        long generation
) {
    public FilterSnapshot {
        filter = Objects.requireNonNull(filter, "filter");
        selectedFilter = Objects.requireNonNull(selectedFilter, "selectedFilter");
        strength = normalizeStrength(strength);
        selectedStrength = normalizeStrength(selectedStrength);
        overrideOwner = overrideOwner == null ? "" : overrideOwner;
        fallbackReason = fallbackReason == null ? FilterFallbackReason.NONE : fallbackReason;
    }

    private static float normalizeStrength(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
