package com.xkmxz.prismod.client.filter.legacy;

import com.google.gson.JsonObject;

/**
 * 不可变的 LUT 调试参数。所有构造入口都会把非有限值归零并裁剪到支持范围。
 * 预览参数独立于普通滤镜强度，不会写入 PrismodClientConfig。
 */
public record LutDebugSettings(
        float intensity,
        float exposure,
        float contrast,
        float highlights,
        float shadows,
        float saturation,
        float temperature,
        float tint,
        float gamma
) {
    public LutDebugSettings {
        intensity = clamp(intensity, 0.0F, 100.0F, 0.0F);
        exposure = clamp(exposure, -100.0F, 100.0F, 0.0F);
        contrast = clamp(contrast, 0.0F, 200.0F, 100.0F);
        highlights = clamp(highlights, -100.0F, 100.0F, 0.0F);
        shadows = clamp(shadows, -100.0F, 100.0F, 0.0F);
        saturation = clamp(saturation, 0.0F, 200.0F, 100.0F);
        temperature = clamp(temperature, -100.0F, 100.0F, 0.0F);
        tint = clamp(tint, -100.0F, 100.0F, 0.0F);
        gamma = clamp(gamma, 10.0F, 300.0F, 100.0F);
    }

    public static LutDebugSettings defaults() {
        return new LutDebugSettings(100.0F, 0.0F, 100.0F, 0.0F, 0.0F,
                100.0F, 0.0F, 0.0F, 100.0F);
    }

    public static LutDebugSettings fromJson(JsonObject object) {
        if (object == null) return defaults();
        return new LutDebugSettings(
                number(object, "intensity", 100.0F),
                number(object, "exposure", 0.0F),
                number(object, "contrast", 100.0F),
                number(object, "highlights", 0.0F),
                number(object, "shadows", 0.0F),
                number(object, "saturation", 100.0F),
                number(object, "temperature", 0.0F),
                number(object, "tint", 0.0F),
                number(object, "gamma", 100.0F));
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("intensity", intensity);
        object.addProperty("exposure", exposure);
        object.addProperty("contrast", contrast);
        object.addProperty("highlights", highlights);
        object.addProperty("shadows", shadows);
        object.addProperty("saturation", saturation);
        object.addProperty("temperature", temperature);
        object.addProperty("tint", tint);
        object.addProperty("gamma", gamma);
        return object;
    }

    private static float number(JsonObject object, String name, float fallback) {
        try {
            return object.has(name) ? object.get(name).getAsFloat() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
