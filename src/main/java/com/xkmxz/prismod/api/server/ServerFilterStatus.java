package com.xkmxz.prismod.api.server;

import net.minecraft.resources.ResourceLocation;

/** Immutable summary of server-owned filter policy state. */
public record ServerFilterStatus(
        ResourceLocation globalSelection,
        float globalStrength,
        int globalOverrideCount,
        int playerSelectionCount,
        int playerOverrideCount,
        long generation
) {
    public ServerFilterStatus {
        globalSelection = globalSelection;
        globalStrength = Float.isFinite(globalStrength) ? Math.max(0.0F, Math.min(1.0F, globalStrength)) : 0.0F;
    }
}
