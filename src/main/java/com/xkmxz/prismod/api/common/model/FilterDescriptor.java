package com.xkmxz.prismod.api.common.model;

import com.xkmxz.prismod.api.common.state.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.FilterType;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable public description of a discovered or API-registered filter. */
public record FilterDescriptor(
        ResourceLocation id,
        String ownerId,
        FilterType type,
        String translationKey,
        float defaultStrength,
        boolean available,
        FilterFailureReason failureReason,
        String failureDetail
) {
    public FilterDescriptor {
        id = Objects.requireNonNull(id, "id");
        ownerId = ownerId == null ? "" : ownerId;
        type = type == null ? FilterType.UNKNOWN : type;
        defaultStrength = normalizeStrength(defaultStrength);
        failureReason = failureReason == null ? FilterFailureReason.NONE : failureReason;
        failureDetail = failureDetail == null ? "" : failureDetail;
    }

    private static float normalizeStrength(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
