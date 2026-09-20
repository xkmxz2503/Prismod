package com.xkmxz.prismod.client.config;

import com.xkmxz.prismod.client.filter.LutDebugSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class LutPresetStoreTest {
    @Test void savesByFilterIdAndIgnoresUnknownFields() throws Exception {
        var file = Files.createTempFile("prismod-lut", ".json");
        var store = new LutPresetStore(file);
        var settings = new LutDebugSettings(.4F, 1, 0, 0, 0, 1.1F, 0, 0, 1.2F);
        store.put("example:cinematic", settings);
        var loaded = new LutPresetStore(file);
        assertEquals(settings, loaded.get("example:cinematic"));
        assertEquals(LutDebugSettings.defaults(), loaded.get("example:missing"));
    }

    @Test void corruptJsonFallsBackToDefaults() throws Exception {
        var file = Files.createTempFile("prismod-lut", ".json");
        Files.writeString(file, "{broken");
        assertEquals(LutDebugSettings.defaults(), new LutPresetStore(file).get("example:x"));
    }

    @Test void unknownVersionIsRejected() throws Exception {
        var file = Files.createTempFile("prismod-lut", ".json");
        Files.writeString(file, "{\"schema\":\"prismod.lut_presets\",\"format_version\":99,\"presets\":{}}" );
        assertEquals(LutDebugSettings.defaults(), new LutPresetStore(file).get("example:x"));
    }
}
