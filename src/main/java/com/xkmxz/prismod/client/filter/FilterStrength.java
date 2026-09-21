package com.xkmxz.prismod.client.filter;

/** Internal strength normalization shared by client filter state and configuration. */
public final class FilterStrength {
    private FilterStrength() {
    }

    /** Non-finite values become zero; finite values are clamped to [0, 1]. */
    public static float normalize(double strength) {
        if (!Double.isFinite(strength)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, strength));
    }
}
