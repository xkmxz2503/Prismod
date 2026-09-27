package com.xkmxz.prismod.api.common.request;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Serializable request shared by future client, network and command adapters. */
public record FilterOverrideRequest(String ownerId, ResourceLocation filter, float strength, int priority) {
    public FilterOverrideRequest {
        ownerId = normalizeOwner(ownerId);
        filter = Objects.requireNonNull(filter, "filter");
        strength = normalizeStrength(strength);
    }

    private static String normalizeOwner(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        return value.trim();
    }

    private static float normalizeStrength(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
