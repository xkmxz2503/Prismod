package com.xkmxz.prismod.client.filter.debug;

import com.google.gson.JsonParser;
import com.xkmxz.prismod.client.filter.legacy.LutDebugSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LutDebugSettingsTest {
    @Test void defaultsAreNeutral() {
        LutDebugSettings settings = LutDebugSettings.defaults();
        assertEquals(100.0F, settings.intensity());
        assertEquals(100.0F, settings.saturation());
        assertEquals(100.0F, settings.gamma());
    }

    @Test void valuesAreFiniteAndClamped() {
        LutDebugSettings settings = new LutDebugSettings(Float.NaN, 999, -999, Float.POSITIVE_INFINITY,
                -999, 999, 200, -200, 0);
        assertEquals(0.0F, settings.intensity());
        assertEquals(100.0F, settings.exposure());
        assertEquals(0.0F, settings.contrast());
        assertEquals(0.0F, settings.highlights());
        assertEquals(-100.0F, settings.shadows());
        assertEquals(200.0F, settings.saturation());
        assertEquals(100.0F, settings.temperature());
        assertEquals(-100.0F, settings.tint());
        assertEquals(10.0F, settings.gamma());
    }

    @Test void serializesAndReads() {
        LutDebugSettings original = new LutDebugSettings(50F, 20F, 80F, 40F, -50F, 120F, 10F, -10F, 180F);
        assertEquals(original, LutDebugSettings.fromJson(JsonParser.parseString(original.toJson().toString()).getAsJsonObject()));
    }
}
