package com.xkmxz.prismod.client.filter.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterDebugSettingsTest {
    private static final String[] FIELDS = {"intensity", "exposure", "contrast", "highlights", "shadows", "saturation", "temperature", "tint", "gamma"};

    @Test void defaultsAreNeutral() { assertArrayEquals(new float[]{100, 0, 100, 0, 0, 100, 0, 0, 100}, values(FilterDebugSettings.defaults())); }

    @Test void preservesValuesInsideRangesAndAtBothBoundaries() {
        float[][] cases = {{40, 20, 80, 40, -50, 120, 10, -10, 180}, {0, -100, 0, -100, -100, 0, -100, -100, 10}, {100, 100, 200, 100, 100, 200, 100, 100, 300}};
        for (float[] input : cases) assertArrayEquals(input, values(settings(input)));
    }

    @Test void clipsFiniteValuesOutsideRanges() {
        assertArrayEquals(new float[]{0, -100, 0, -100, -100, 0, -100, -100, 10}, values(settings(filled(-999))));
        assertArrayEquals(new float[]{100, 100, 200, 100, 100, 200, 100, 100, 300}, values(settings(filled(999))));
    }

    @Test void nonFiniteValuesUseFieldFallbacksRatherThanRangeBounds() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) assertArrayEquals(new float[]{0, 0, 100, 0, 0, 100, 0, 0, 100}, values(settings(filled(value))));
    }

    @Test void jsonRoundTripPreservesAllNineNamedFields() {
        FilterDebugSettings expected = settings(new float[]{40, 20, 80, 40, -50, 120, 10, -10, 180});
        JsonObject json = expected.toJson();
        assertEquals(java.util.Set.of(FIELDS), json.keySet());
        float[] expectedValues = values(expected);
        for (int i = 0; i < FIELDS.length; i++) assertEquals(expectedValues[i], json.get(FIELDS[i]).getAsFloat(), FIELDS[i]);
        assertEquals(expected, FilterDebugSettings.fromJson(JsonParser.parseString(json.toString()).getAsJsonObject()));
    }

    @Test void missingJsonFieldsUseDefaults() {
        assertEquals(FilterDebugSettings.defaults(), FilterDebugSettings.fromJson(null));
        assertEquals(FilterDebugSettings.defaults(), FilterDebugSettings.fromJson(new JsonObject()));
        JsonObject partial = new JsonObject(); partial.addProperty("exposure", 75F);
        assertEquals(new FilterDebugSettings(100, 75, 100, 0, 0, 100, 0, 0, 100), FilterDebugSettings.fromJson(partial));
    }

    @Test void malformedJsonFieldsUseDefaults() {
        for (String field : FIELDS) for (var invalid : java.util.List.of(JsonNull.INSTANCE, new JsonArray(), new JsonObject(), new com.google.gson.JsonPrimitive("invalid"))) {
            JsonObject json = new JsonObject(); json.add(field, invalid);
            assertEquals(FilterDebugSettings.defaults(), FilterDebugSettings.fromJson(json), field);
        }
    }

    @Test void jsonNumericStringsAndOutOfRangeNumbersRetainNormalization() {
        JsonObject json = new JsonObject(); for (String field : FIELDS) json.addProperty(field, "999");
        assertArrayEquals(new float[]{100, 100, 200, 100, 100, 200, 100, 100, 300}, values(FilterDebugSettings.fromJson(json)));
        for (String value : new String[]{"NaN", "Infinity", "-Infinity"}) { for (String field : FIELDS) json.addProperty(field, value); assertArrayEquals(new float[]{0, 0, 100, 0, 0, 100, 0, 0, 100}, values(FilterDebugSettings.fromJson(json))); }
    }

    @Test void sparseApiAdjustmentsOnlyChangeSelectedFields() {
        FilterDebugSettings base = new FilterDebugSettings(100, 20, 130, 40, 50, 120, 10, 20, 140);
        FilterDebugSettings result = base.apply(FilterAdjustments.builder().exposure(75.0F).build());
        assertEquals(75.0F, result.exposure()); assertEquals(base.contrast(), result.contrast()); assertEquals(base.saturation(), result.saturation()); assertEquals(base.gamma(), result.gamma());
    }

    @Test void clearingApiAdjustmentLeavesCurrentBaseValue() {
        FilterDebugSettings base = new FilterDebugSettings(100, 20, 130, 0, 0, 100, 0, 0, 100);
        FilterDebugSettings result = base.apply(FilterAdjustments.builder().clearExposure().build()); assertEquals(base.exposure(), result.exposure());
    }

    private static float[] filled(float value) { float[] values = new float[9]; java.util.Arrays.fill(values, value); return values; }
    private static FilterDebugSettings settings(float[] v) { return new FilterDebugSettings(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]); }
    private static float[] values(FilterDebugSettings s) { return new float[]{s.intensity(), s.exposure(), s.contrast(), s.highlights(), s.shadows(), s.saturation(), s.temperature(), s.tint(), s.gamma()}; }
}
