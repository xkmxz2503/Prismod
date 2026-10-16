package com.xkmxz.prismod.client.filter.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterDebugSettingsTest {
    private static final String[] FIELDS = {
            "intensity", "exposure", "contrast", "highlights", "shadows",
            "saturation", "temperature", "tint", "gamma"
    };

    @Test
    void defaultsAreNeutral() {
        assertArrayEquals(new float[]{1, 0, 0, 0, 0, 1, 0, 0, 1}, values(FilterDebugSettings.defaults()));
    }

    @Test
    void preservesValuesInsideRangesAndAtBothBoundaries() {
        float[][] cases = {
                {.4F, .2F, -.3F, .4F, -.5F, 1.2F, .1F, -.1F, 1.8F},
                {0, -2, -1, -1, -1, 0, -1, -1, .1F},
                {1, 2, 1, 1, 1, 2, 1, 1, 3}
        };
        for (float[] input : cases) {
            assertArrayEquals(input, values(settings(input)));
        }
    }

    @Test
    void clipsFiniteValuesOutsideRanges() {
        assertArrayEquals(new float[]{0, -2, -1, -1, -1, 0, -1, -1, .1F},
                values(settings(filled(-99))));
        assertArrayEquals(new float[]{1, 2, 1, 1, 1, 2, 1, 1, 3},
                values(settings(filled(99))));
    }

    @Test
    void nonFiniteValuesUseFieldFallbacksRatherThanRangeBounds() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertArrayEquals(new float[]{0, 0, 0, 0, 0, 1, 0, 0, 1},
                    values(settings(filled(value))));
        }
    }

    @Test
    void jsonRoundTripPreservesAllNineNamedFields() {
        FilterDebugSettings expected = settings(new float[]{.4F, .2F, -.3F, .4F, -.5F, 1.2F, .1F, -.1F, 1.8F});
        JsonObject json = expected.toJson();
        assertEquals(java.util.Set.of(FIELDS), json.keySet());
        float[] expectedValues = values(expected);
        for (int i = 0; i < FIELDS.length; i++) {
            assertEquals(expectedValues[i], json.get(FIELDS[i]).getAsFloat(), FIELDS[i]);
        }
        assertEquals(expected, FilterDebugSettings.fromJson(JsonParser.parseString(json.toString()).getAsJsonObject()));
    }

    @Test
    void missingJsonFieldsUseDefaults() {
        assertEquals(FilterDebugSettings.defaults(), FilterDebugSettings.fromJson(null));
        assertEquals(FilterDebugSettings.defaults(), FilterDebugSettings.fromJson(new JsonObject()));
        JsonObject partial = new JsonObject();
        partial.addProperty("exposure", .75F);
        assertEquals(new FilterDebugSettings(1, .75F, 0, 0, 0, 1, 0, 0, 1),
                FilterDebugSettings.fromJson(partial));
    }

    @Test
    void malformedJsonFieldsUseDefaults() {
        for (String field : FIELDS) {
            for (var invalid : java.util.List.of(JsonNull.INSTANCE, new JsonArray(), new JsonObject(),
                    new com.google.gson.JsonPrimitive("invalid"))) {
                JsonObject json = new JsonObject();
                json.add(field, invalid);
                assertEquals(FilterDebugSettings.defaults(), FilterDebugSettings.fromJson(json), field);
            }
        }
    }

    @Test
    void jsonNumericStringsAndOutOfRangeNumbersRetainExistingNormalization() {
        JsonObject json = new JsonObject();
        for (String field : FIELDS) json.addProperty(field, "99");
        assertArrayEquals(new float[]{1, 2, 1, 1, 1, 2, 1, 1, 3}, values(FilterDebugSettings.fromJson(json)));
        for (String value : new String[]{"NaN", "Infinity", "-Infinity"}) {
            for (String field : FIELDS) json.addProperty(field, value);
            assertArrayEquals(new float[]{0, 0, 0, 0, 0, 1, 0, 0, 1}, values(FilterDebugSettings.fromJson(json)));
        }
    }

    @Test
    void sparseApiAdjustmentsOnlyChangeSelectedFields() {
        FilterDebugSettings base = new FilterDebugSettings(1, .2F, .3F, .4F, .5F, 1.2F, .1F, .2F, 1.4F);
        FilterDebugSettings result = base.apply(FilterAdjustments.builder().exposure(1.5F).build());
        assertEquals(1.5F, result.exposure());
        assertEquals(base.contrast(), result.contrast());
        assertEquals(base.saturation(), result.saturation());
        assertEquals(base.gamma(), result.gamma());
    }

    @Test
    void clearingApiAdjustmentLeavesCurrentBaseValue() {
        FilterDebugSettings base = new FilterDebugSettings(1, .2F, .3F, 0, 0, 1, 0, 0, 1);
        FilterDebugSettings result = base.apply(FilterAdjustments.builder().clearExposure().build());
        assertEquals(base.exposure(), result.exposure());
    }

    private static float[] filled(float value) {
        float[] values = new float[9];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static FilterDebugSettings settings(float[] v) {
        return new FilterDebugSettings(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
    }

    private static float[] values(FilterDebugSettings s) {
        return new float[]{s.intensity(), s.exposure(), s.contrast(), s.highlights(), s.shadows(),
                s.saturation(), s.temperature(), s.tint(), s.gamma()};
    }
}
