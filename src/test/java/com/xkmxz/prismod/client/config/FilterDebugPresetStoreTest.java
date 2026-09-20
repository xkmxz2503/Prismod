package com.xkmxz.prismod.client.config;

import com.google.gson.JsonParser;
import com.xkmxz.prismod.client.filter.FilterDebugSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FilterDebugPresetStoreTest {
    @TempDir Path temp;

    @Test
    void namespaceAndFilterFilesAreIndependent() throws Exception {
        FilterDebugPresetStore store = new FilterDebugPresetStore(temp);
        FilterDebugSettings first = new FilterDebugSettings(.25F, .5F, 0, 0, 0, 1, 0, 0, 1);
        FilterDebugSettings second = new FilterDebugSettings(.75F, -.5F, 0, 0, 0, 1, 0, 0, 1);
        store.put("example", "cinematic", first);
        store.put("other", "cinematic", second);
        assertEquals(first, store.get("example", "cinematic"));
        assertEquals(second, store.get("other", "cinematic"));
        assertEquals(temp.resolve("example/cinematic.json"), store.file("example", "cinematic"));
    }

    @Test
    void missingLegacyFileAndMissingPresetUseDefaults() {
        FilterDebugPresetStore store = new FilterDebugPresetStore(temp);
        assertEquals(FilterDebugSettings.defaults(), store.get("example", "missing"));
        assertFalse(Files.exists(temp.resolve("lut-presets.json")));
    }

    @Test
    void invalidIdentitySchemaAndVersionUseDefaults() throws Exception {
        Path file = temp.resolve("example/cinematic.json");
        Files.createDirectories(file.getParent());
        String validSettings = new FilterDebugSettings(.4F, 0, 0, 0, 0, 1, 0, 0, 1).toJson().toString();
        Files.writeString(file, "{\"schema\":\"prismod.other\",\"format_version\":1,\"filter\":\"example:cinematic\",\"settings\":" + validSettings + "}");
        assertEquals(FilterDebugSettings.defaults(), new FilterDebugPresetStore(temp).get("example", "cinematic"));
        Files.writeString(file, "{\"schema\":\"prismod.filter_debug\",\"format_version\":2,\"filter\":\"example:cinematic\",\"settings\":" + validSettings + "}");
        assertEquals(FilterDebugSettings.defaults(), new FilterDebugPresetStore(temp).get("example", "cinematic"));
        Files.writeString(file, "{\"schema\":\"prismod.filter_debug\",\"format_version\":1,\"filter\":\"other:cinematic\",\"settings\":" + validSettings + "}");
        assertEquals(FilterDebugSettings.defaults(), new FilterDebugPresetStore(temp).get("example", "cinematic"));
    }

    @Test
    void saveAndReloadPreservesAllNineFields() throws Exception {
        FilterDebugSettings expected = new FilterDebugSettings(.4F, .2F, -.3F, .4F, -.5F, 1.2F, .1F, -.1F, 1.8F);
        FilterDebugPresetStore store = new FilterDebugPresetStore(temp);
        store.put("example", "cinematic", expected);
        assertEquals(expected, new FilterDebugPresetStore(temp).get("example", "cinematic"));
        assertEquals("example:cinematic", JsonParser.parseString(Files.readString(temp.resolve("example/cinematic.json"))).getAsJsonObject().get("filter").getAsString());
    }
}
