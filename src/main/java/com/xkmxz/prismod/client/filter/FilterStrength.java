package com.xkmxz.prismod.client.filter;

/** Internal strength normalization shared by client filter state and configuration. */
public final class FilterStrength {
    private FilterStrength() {
    }

    /** 非有限值归零；有限值裁剪到百分制 [0, 100]。 */
    public static float normalize(double strength) {
        if (!Double.isFinite(strength)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(100.0D, strength));
    }
}
