package com.xkmxz.prismod.api.common.request;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Serializable selection request reserved for future server adapters. */
public record FilterSelectionRequest(ResourceLocation filter, float strength) {
    public FilterSelectionRequest {
        filter = Objects.requireNonNull(filter, "filter");
        if (!Float.isFinite(strength)) strength = 0.0F;
        strength = Math.max(0.0F, Math.min(1.0F, strength));
    }
}
