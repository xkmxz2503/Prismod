package com.xkmxz.prismod.api.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable client-side view of one filter selection. */
public record FilterSnapshot(ResourceLocation filter, float strength, boolean forced, boolean renderAvailable) {
    public FilterSnapshot {
        filter = Objects.requireNonNull(filter, "filter");
        strength = normalizeStrength(strength);
    }

    /** Non-finite values become zero; finite values are clamped to [0, 1]. */
    public static float normalizeStrength(double strength) {
        if (!Double.isFinite(strength)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, strength));
    }
}
