package com.xkmxz.prismod.client.filter;

import com.google.gson.JsonObject;

/** 通用于 LUT 和 post_chain 滤镜的调参快照。 */
public record FilterDebugSettings(
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
    public FilterDebugSettings {
        intensity = clamp(intensity, 0.0F, 1.0F, 0.0F);
        exposure = clamp(exposure, -2.0F, 2.0F, 0.0F);
        contrast = clamp(contrast, -1.0F, 1.0F, 0.0F);
        highlights = clamp(highlights, -1.0F, 1.0F, 0.0F);
        shadows = clamp(shadows, -1.0F, 1.0F, 0.0F);
        saturation = clamp(saturation, 0.0F, 2.0F, 1.0F);
        temperature = clamp(temperature, -1.0F, 1.0F, 0.0F);
        tint = clamp(tint, -1.0F, 1.0F, 0.0F);
        gamma = clamp(gamma, 0.1F, 3.0F, 1.0F);
    }

    public static FilterDebugSettings defaults() {
        return new FilterDebugSettings(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
    }

    public static FilterDebugSettings fromJson(JsonObject object) {
        if (object == null) return defaults();
        return new FilterDebugSettings(number(object, "intensity", 1.0F), number(object, "exposure", 0.0F),
                number(object, "contrast", 0.0F), number(object, "highlights", 0.0F), number(object, "shadows", 0.0F),
                number(object, "saturation", 1.0F), number(object, "temperature", 0.0F), number(object, "tint", 0.0F),
                number(object, "gamma", 1.0F));
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("intensity", intensity); object.addProperty("exposure", exposure);
        object.addProperty("contrast", contrast); object.addProperty("highlights", highlights);
        object.addProperty("shadows", shadows); object.addProperty("saturation", saturation);
        object.addProperty("temperature", temperature); object.addProperty("tint", tint);
        object.addProperty("gamma", gamma);
        return object;
    }

    public static FilterDebugSettings from(LutDebugSettings settings) {
        if (settings == null) return defaults();
        return new FilterDebugSettings(settings.intensity(), settings.exposure(), settings.contrast(), settings.highlights(),
                settings.shadows(), settings.saturation(), settings.temperature(), settings.tint(), settings.gamma());
    }

    public LutDebugSettings toLutSettings() {
        return new LutDebugSettings(intensity, exposure, contrast, highlights, shadows, saturation, temperature, tint, gamma);
    }

    private static float number(JsonObject object, String name, float fallback) {
        try { return object.has(name) ? object.get(name).getAsFloat() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
