package com.xkmxz.prismod.api.client;

/** Optional presentation and default-strength metadata for a registered filter. */
public record CustomFilterMetadata(String translationKey, float defaultStrength) {
    public CustomFilterMetadata {
        defaultStrength = FilterSnapshot.normalizeStrength(defaultStrength);
    }

    public static CustomFilterMetadata defaults() {
        return new CustomFilterMetadata(null, 1.0F);
    }
}
