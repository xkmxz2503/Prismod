package com.xkmxz.prismod.client;

public record FilterState(FilterId id, float strength, boolean forced) {
    public FilterState {
        id = id == null ? FilterId.ORIGINAL : id;
        strength = normalizeStrength(strength);
    }

    /** 非有限值按零强度处理，有限值限制在闭区间 [0, 1]。 */
    public static float normalizeStrength(double strength) {
        if (!Double.isFinite(strength)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, strength));
    }
}
