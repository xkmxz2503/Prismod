package com.xkmxz.prismod.client.filter.debug;

import com.google.gson.JsonObject;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;
import com.xkmxz.prismod.client.filter.legacy.LutDebugSettings;

/**
 * 通用于 LUT 和 PostChain 滤镜的内部不可变调参快照。
 *
 * <p>有限值在构造时裁剪到字段范围；非有限值按字段默认值处理，但
 * {@code intensity} 的非有限值回退为 {@code 0}。本类型不执行渲染或保存文件。
 * 由调试界面、预设存储和渲染器使用，不属于公开 API 契约。</p>
 *
 * @param intensity 调参强度，范围 [0, 1]，默认 1，非有限值回退为 0
 * @param exposure 曝光（EV），范围 [-2, 2]，默认 0
 * @param contrast 对比度，范围 [-1, 1]，默认 0
 * @param highlights 高光，范围 [-1, 1]，默认 0
 * @param shadows 阴影，范围 [-1, 1]，默认 0
 * @param saturation 饱和度，范围 [0, 2]，默认 1
 * @param temperature 色温，范围 [-1, 1]，默认 0
 * @param tint 色调，范围 [-1, 1]，默认 0
 * @param gamma 伽马，范围 [0.1, 3]，默认 1
 */
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
    /** 创建调参快照，并归一化全部九个字段。 */
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

    /** @return 强度为 1、其他颜色调整为中性值的快照 */
    public static FilterDebugSettings defaults() {
        return new FilterDebugSettings(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
    }

    /**
     * 从预设 settings 对象读取调参；缺失或无法解析的字段使用默认值，之后执行构造归一化。
     * 可解析的数值字符串沿用现有兼容行为。
     *
     * @param object settings 对象；为 {@code null} 时返回默认快照
     * @return 归一化后的调参快照
     */
    public static FilterDebugSettings fromJson(JsonObject object) {
        if (object == null) return defaults();
        return new FilterDebugSettings(number(object, "intensity", 1.0F), number(object, "exposure", 0.0F),
                number(object, "contrast", 0.0F), number(object, "highlights", 0.0F), number(object, "shadows", 0.0F),
                number(object, "saturation", 1.0F), number(object, "temperature", 0.0F), number(object, "tint", 0.0F),
                number(object, "gamma", 1.0F));
    }

    /** @return 包含全部九个字段的新 JSON 对象；不包含预设文件的 schema 或身份信息 */
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

    /** 将公开 API 的稀疏调参叠加到当前内部调试值。 */
    public FilterDebugSettings apply(FilterAdjustments adjustments) {
        if (adjustments == null || adjustments.isEmpty()) return this;
        return new FilterDebugSettings(intensity,
                value(adjustments.exposure(), adjustments.clearsExposure(), exposure),
                value(adjustments.contrast(), adjustments.clearsContrast(), contrast),
                value(adjustments.highlights(), adjustments.clearsHighlights(), highlights),
                value(adjustments.shadows(), adjustments.clearsShadows(), shadows),
                value(adjustments.saturation(), adjustments.clearsSaturation(), saturation),
                value(adjustments.temperature(), adjustments.clearsTemperature(), temperature),
                value(adjustments.tint(), adjustments.clearsTint(), tint),
                value(adjustments.gamma(), adjustments.clearsGamma(), gamma));
    }

    private static float value(Float value, boolean clear, float current) {
        return value == null || clear ? current : value;
    }

    private static float number(JsonObject object, String name, float fallback) {
        try { return object.has(name) ? object.get(name).getAsFloat() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
