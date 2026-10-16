package com.xkmxz.prismod.client.filter.debug;

/** 将公开的百分制参数转换为现有 shader uniform 使用的范围。 */
public final class FilterParameterScale {
    private FilterParameterScale() { }

    public static float strength(float percent) { return clamp(percent, 0.0F, 100.0F) / 100.0F; }
    public static float exposure(float percent) { return clamp(percent, -100.0F, 100.0F) / 50.0F; }
    public static float contrast(float percent) { return (clamp(percent, 0.0F, 200.0F) - 100.0F) / 100.0F; }
    public static float signed(float percent) { return clamp(percent, -100.0F, 100.0F) / 100.0F; }
    public static float saturation(float percent) { return clamp(percent, 0.0F, 200.0F) / 100.0F; }
    public static float gamma(float percent) { return clamp(percent, 10.0F, 300.0F) / 100.0F; }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
