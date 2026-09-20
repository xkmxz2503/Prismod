package com.xkmxz.prismod.client.pack;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PrismodPackLoaderTest {
    @TempDir Path temp;

    @Test void parsesV1ManifestAndIgnoresUnknownFields() {
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString("{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"example\",\"name\":\"Example\",\"unknown\":true,\"filters\":[{\"id\":\"gray\",\"path\":\"assets/example/filters/gray\"}]}").getAsJsonObject());
        assertEquals("example", metadata.namespace());
        assertEquals("gray", metadata.filters().get(0).id());
    }

    @Test void rejectsInvalidManifest() {
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseManifest(JsonParser.parseString("{}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseManifest(JsonParser.parseString("{\"schema\":\"prismod.resource_pack\",\"format_version\":2,\"namespace\":\"x\",\"filters\":[]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseManifest(JsonParser.parseString("{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"minecraft\",\"filters\":[]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseManifest(JsonParser.parseString("{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"x\",\"filters\":[{\"id\":\"a\",\"path\":\"assets/x/filters/a\"},{\"id\":\"a\",\"path\":\"assets/x/filters/b\"}]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseManifest(JsonParser.parseString("{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"x\",\"filters\":[{\"id\":\"a\",\"path\":\"assets/other/filters/a\"}]}").getAsJsonObject()));
    }

    @Test void directoryAndZipRequireDeclaredFilterJson() throws IOException {
        Path directory = Files.createDirectories(temp.resolve("directory"));
        Files.writeString(directory.resolve(PrismodPackLoader.MANIFEST_FILE), manifest("example", "gray"));
        Path filter = Files.createDirectories(directory.resolve("assets/example/filters/gray"));
        Files.writeString(filter.resolve("filter.json"), "{}");
        assertEquals(1, PrismodPackLoader.scan(temp).size());
        Path zip = temp.resolve("zip.zip");
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream archive = new ZipOutputStream(out)) {
            put(archive, PrismodPackLoader.MANIFEST_FILE, manifest("zipns", "one"));
            put(archive, "assets/zipns/filters/one/filter.json", "{}");
        }
        assertEquals(2, PrismodPackLoader.scan(temp).size());
    }

    @Test void oldPackIsNotScanned() throws IOException {
        Path old = Files.createDirectories(temp.resolve("old"));
        Files.writeString(old.resolve(PrismodPackLoader.META_FILE), "{\"namespace\":\"old\"}");
        assertTrue(PrismodPackLoader.scan(temp).isEmpty());
    }

    private static String manifest(String namespace, String id) { return "{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"" + namespace + "\",\"filters\":[{\"id\":\"" + id + "\",\"path\":\"assets/" + namespace + "/filters/" + id + "\"}]}"; }
    private static void put(ZipOutputStream zip, String name, String content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry(); }
}
