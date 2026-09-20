package com.xkmxz.prismod.client.filter;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LutDebugSettingsTest {
    @Test void defaultsAreNeutral() {
        LutDebugSettings settings = LutDebugSettings.defaults();
        assertEquals(1.0F, settings.intensity());
        assertEquals(1.0F, settings.saturation());
        assertEquals(1.0F, settings.gamma());
    }

    @Test void valuesAreFiniteAndClamped() {
        LutDebugSettings settings = new LutDebugSettings(Float.NaN, 99, -99, Float.POSITIVE_INFINITY,
                -99, 99, 2, -2, 0);
        assertEquals(0.0F, settings.intensity());
        assertEquals(2.0F, settings.exposure());
        assertEquals(-1.0F, settings.contrast());
        assertEquals(0.0F, settings.highlights());
        assertEquals(-1.0F, settings.shadows());
        assertEquals(2.0F, settings.saturation());
        assertEquals(1.0F, settings.temperature());
        assertEquals(-1.0F, settings.tint());
        assertEquals(0.1F, settings.gamma());
    }

    @Test void serializesAndReads() {
        LutDebugSettings original = new LutDebugSettings(.5F, .2F, -.3F, .4F, -.5F, 1.2F, .1F, -.1F, 1.8F);
        assertEquals(original, LutDebugSettings.fromJson(JsonParser.parseString(original.toJson().toString()).getAsJsonObject()));
    }
}
