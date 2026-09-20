package com.xkmxz.prismod.client.filter;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterManifestTest {
    @Test
    void parsesPostChainAndLutManifests() {
        FilterManifest post = FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"post_chain\",\"display_name\":\"filter.example.gray\",\"source\":\"post.json\"}").getAsJsonObject());
        assertEquals(FilterType.POST_CHAIN, post.type());
        assertEquals(1.0F, post.defaultStrength());

        FilterManifest lut = FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"lut3d\",\"display_name\":\"filter.example.cinematic\",\"source\":\"lut.cube\",\"color_space\":\"sRGB\"}").getAsJsonObject());
        assertEquals(FilterType.LUT3D, lut.type());
        assertEquals("sRGB", lut.colorSpace());
    }

    @Test
    void rejectsInvalidTypeSpecificFieldsAndTraversal() {
        assertThrows(IllegalArgumentException.class, () -> FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"lut3d\",\"display_name\":\"x\",\"source\":\"lut.json\",\"color_space\":\"srgb\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"lut3d\",\"display_name\":\"x\",\"source\":\"lut.cube\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"post_chain\",\"display_name\":\"x\",\"source\":\"../post.json\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"post_chain\",\"display_name\":\"x\",\"source\":\"post.json\",\"default_strength\":1.1}").getAsJsonObject()));
    }

    @Test
    void unknownTypeCanBeSkippedByCaller() {
        assertThrows(FilterManifest.UnknownFilterTypeException.class, () -> FilterManifest.parse(JsonParser.parseString("{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"film_grain\",\"display_name\":\"x\",\"source\":\"grain.json\"}").getAsJsonObject()));
    }
}
