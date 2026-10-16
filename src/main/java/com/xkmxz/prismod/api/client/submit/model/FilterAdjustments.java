package com.xkmxz.prismod.api.client.submit.model;

import java.util.Objects;

/**
 * 客户端滤镜高级调参的稀疏更新对象。
 *
 * <p>未调用对应 builder 方法的字段保持当前有效值；调用 {@code clearXxx}
 * 会让该字段恢复继承当前调试预设。数值字段在构建时按现有调参范围裁剪。</p>
 */
public final class FilterAdjustments {
    private final Float exposure;
    private final boolean clearExposure;
    private final Float contrast;
    private final boolean clearContrast;
    private final Float highlights;
    private final boolean clearHighlights;
    private final Float shadows;
    private final boolean clearShadows;
    private final Float saturation;
    private final boolean clearSaturation;
    private final Float temperature;
    private final boolean clearTemperature;
    private final Float tint;
    private final boolean clearTint;
    private final Float gamma;
    private final boolean clearGamma;

    private FilterAdjustments(Builder builder) {
        exposure = builder.exposure;
        clearExposure = builder.clearExposure;
        contrast = builder.contrast;
        clearContrast = builder.clearContrast;
        highlights = builder.highlights;
        clearHighlights = builder.clearHighlights;
        shadows = builder.shadows;
        clearShadows = builder.clearShadows;
        saturation = builder.saturation;
        clearSaturation = builder.clearSaturation;
        temperature = builder.temperature;
        clearTemperature = builder.clearTemperature;
        tint = builder.tint;
        clearTint = builder.clearTint;
        gamma = builder.gamma;
        clearGamma = builder.clearGamma;
    }

    public static Builder builder() { return new Builder(); }

    public Float exposure() { return exposure; }
    public boolean clearsExposure() { return clearExposure; }
    public Float contrast() { return contrast; }
    public boolean clearsContrast() { return clearContrast; }
    public Float highlights() { return highlights; }
    public boolean clearsHighlights() { return clearHighlights; }
    public Float shadows() { return shadows; }
    public boolean clearsShadows() { return clearShadows; }
    public Float saturation() { return saturation; }
    public boolean clearsSaturation() { return clearSaturation; }
    public Float temperature() { return temperature; }
    public boolean clearsTemperature() { return clearTemperature; }
    public Float tint() { return tint; }
    public boolean clearsTint() { return clearTint; }
    public Float gamma() { return gamma; }
    public boolean clearsGamma() { return clearGamma; }

    public boolean isEmpty() {
        return exposure == null && !clearExposure && contrast == null && !clearContrast
                && highlights == null && !clearHighlights && shadows == null && !clearShadows
                && saturation == null && !clearSaturation && temperature == null && !clearTemperature
                && tint == null && !clearTint && gamma == null && !clearGamma;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FilterAdjustments that)) return false;
        return clearExposure == that.clearExposure && clearContrast == that.clearContrast
                && clearHighlights == that.clearHighlights && clearShadows == that.clearShadows
                && clearSaturation == that.clearSaturation && clearTemperature == that.clearTemperature
                && clearTint == that.clearTint && clearGamma == that.clearGamma
                && Objects.equals(exposure, that.exposure) && Objects.equals(contrast, that.contrast)
                && Objects.equals(highlights, that.highlights) && Objects.equals(shadows, that.shadows)
                && Objects.equals(saturation, that.saturation) && Objects.equals(temperature, that.temperature)
                && Objects.equals(tint, that.tint) && Objects.equals(gamma, that.gamma);
    }

    @Override public int hashCode() {
        return Objects.hash(exposure, clearExposure, contrast, clearContrast, highlights, clearHighlights,
                shadows, clearShadows, saturation, clearSaturation, temperature, clearTemperature,
                tint, clearTint, gamma, clearGamma);
    }

    /** 合并同一来源的两次稀疏更新；后一次只覆盖它明确触及的字段。 */
    public static FilterAdjustments merge(FilterAdjustments previous, FilterAdjustments update) {
        if (update == null || update.isEmpty()) return previous;
        Builder builder = builder();
        copyUnlessTouched(builder, previous, update, Field.EXPOSURE);
        copyUnlessTouched(builder, previous, update, Field.CONTRAST);
        copyUnlessTouched(builder, previous, update, Field.HIGHLIGHTS);
        copyUnlessTouched(builder, previous, update, Field.SHADOWS);
        copyUnlessTouched(builder, previous, update, Field.SATURATION);
        copyUnlessTouched(builder, previous, update, Field.TEMPERATURE);
        copyUnlessTouched(builder, previous, update, Field.TINT);
        copyUnlessTouched(builder, previous, update, Field.GAMMA);
        return builder.build();
    }

    private static void copyUnlessTouched(Builder builder, FilterAdjustments previous,
                                          FilterAdjustments update, Field field) {
        if (update.touched(field)) {
            if (!update.clears(field)) builder.value(field, update.value(field));
        } else if (previous != null && previous.touched(field) && !previous.clears(field)) {
            builder.value(field, previous.value(field));
        }
    }

    private boolean touched(Field field) { return clears(field) || value(field) != null; }
    private boolean clears(Field field) {
        return switch (field) {
            case EXPOSURE -> clearExposure; case CONTRAST -> clearContrast;
            case HIGHLIGHTS -> clearHighlights; case SHADOWS -> clearShadows;
            case SATURATION -> clearSaturation; case TEMPERATURE -> clearTemperature;
            case TINT -> clearTint; case GAMMA -> clearGamma;
        };
    }
    private Float value(Field field) {
        return switch (field) {
            case EXPOSURE -> exposure; case CONTRAST -> contrast;
            case HIGHLIGHTS -> highlights; case SHADOWS -> shadows;
            case SATURATION -> saturation; case TEMPERATURE -> temperature;
            case TINT -> tint; case GAMMA -> gamma;
        };
    }

    private enum Field { EXPOSURE, CONTRAST, HIGHLIGHTS, SHADOWS, SATURATION, TEMPERATURE, TINT, GAMMA }

    public static final class Builder {
        private Float exposure, contrast, highlights, shadows, saturation, temperature, tint, gamma;
        private boolean clearExposure, clearContrast, clearHighlights, clearShadows;
        private boolean clearSaturation, clearTemperature, clearTint, clearGamma;

        public Builder exposure(float value) { return set(Field.EXPOSURE, value, -2.0F, 2.0F, 0.0F); }
        public Builder contrast(float value) { return set(Field.CONTRAST, value, -1.0F, 1.0F, 0.0F); }
        public Builder highlights(float value) { return set(Field.HIGHLIGHTS, value, -1.0F, 1.0F, 0.0F); }
        public Builder shadows(float value) { return set(Field.SHADOWS, value, -1.0F, 1.0F, 0.0F); }
        public Builder saturation(float value) { return set(Field.SATURATION, value, 0.0F, 2.0F, 1.0F); }
        public Builder temperature(float value) { return set(Field.TEMPERATURE, value, -1.0F, 1.0F, 0.0F); }
        public Builder tint(float value) { return set(Field.TINT, value, -1.0F, 1.0F, 0.0F); }
        public Builder gamma(float value) { return set(Field.GAMMA, value, 0.1F, 3.0F, 1.0F); }

        public Builder clearExposure() { return clear(Field.EXPOSURE); }
        public Builder clearContrast() { return clear(Field.CONTRAST); }
        public Builder clearHighlights() { return clear(Field.HIGHLIGHTS); }
        public Builder clearShadows() { return clear(Field.SHADOWS); }
        public Builder clearSaturation() { return clear(Field.SATURATION); }
        public Builder clearTemperature() { return clear(Field.TEMPERATURE); }
        public Builder clearTint() { return clear(Field.TINT); }
        public Builder clearGamma() { return clear(Field.GAMMA); }

        private Builder set(Field field, float value, float min, float max, float fallback) {
            if (!Float.isFinite(value)) value = fallback;
            value = Math.max(min, Math.min(max, value));
            switch (field) {
                case EXPOSURE -> { exposure = value; clearExposure = false; }
                case CONTRAST -> { contrast = value; clearContrast = false; }
                case HIGHLIGHTS -> { highlights = value; clearHighlights = false; }
                case SHADOWS -> { shadows = value; clearShadows = false; }
                case SATURATION -> { saturation = value; clearSaturation = false; }
                case TEMPERATURE -> { temperature = value; clearTemperature = false; }
                case TINT -> { tint = value; clearTint = false; }
                case GAMMA -> { gamma = value; clearGamma = false; }
            }
            return this;
        }

        private Builder clear(Field field) {
            switch (field) {
                case EXPOSURE -> { exposure = null; clearExposure = true; }
                case CONTRAST -> { contrast = null; clearContrast = true; }
                case HIGHLIGHTS -> { highlights = null; clearHighlights = true; }
                case SHADOWS -> { shadows = null; clearShadows = true; }
                case SATURATION -> { saturation = null; clearSaturation = true; }
                case TEMPERATURE -> { temperature = null; clearTemperature = true; }
                case TINT -> { tint = null; clearTint = true; }
                case GAMMA -> { gamma = null; clearGamma = true; }
            }
            return this;
        }

        private Builder value(Field field, Float value) {
            if (value != null) switch (field) {
                case EXPOSURE -> exposure(value); case CONTRAST -> contrast(value);
                case HIGHLIGHTS -> highlights(value); case SHADOWS -> shadows(value);
                case SATURATION -> saturation(value); case TEMPERATURE -> temperature(value);
                case TINT -> tint(value); case GAMMA -> gamma(value);
            }
            return this;
        }

        public FilterAdjustments build() { return new FilterAdjustments(this); }
    }
}
