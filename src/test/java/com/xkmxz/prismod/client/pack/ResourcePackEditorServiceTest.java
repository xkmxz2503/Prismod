package com.xkmxz.prismod.client.pack;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackEditorServiceTest {
    @TempDir Path temp;

    @Test
    void opensZipIntoEditableDirectoryWithoutDeletingZip() throws Exception {
        Path zip = temp.resolve("example.zip");
        String manifest = manifest("example", "gray");
        try (OutputStream output = Files.newOutputStream(zip); ZipOutputStream archive = new ZipOutputStream(output)) {
            put(archive, PrismodPackLoader.MANIFEST_FILE, manifest);
            put(archive, "assets/example/filters/gray/filter.json", filter());
        }
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest).getAsJsonObject());
        ResourcePackEditorService.Session session = ResourcePackEditorService.open(new PrismodPackLoader.PackCandidate(zip, metadata));
        assertTrue(Files.isDirectory(session.workingDirectory()));
        assertTrue(session.workingDirectory().getFileName().toString().endsWith(".editable"));
        assertTrue(Files.exists(zip));
        assertTrue(session.files().containsKey("assets/example/filters/gray/filter.json"));
    }

    @Test
    void savesEditedManifestAndRejectsNamespaceConflict() throws Exception {
        Path root = pack("one", "gray");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest("one", "gray")).getAsJsonObject());
        ResourcePackEditorService.Session session = ResourcePackEditorService.open(new PrismodPackLoader.PackCandidate(root, metadata));
        ResourcePackEditorService.SaveResult saved = ResourcePackEditorService.save(session, "Updated", "two", Map.of());
        assertTrue(saved.success(), saved.message());
        assertTrue(Files.exists(saved.path().resolve(PrismodPackLoader.MANIFEST_FILE)));
        assertEquals("two", JsonParser.parseString(Files.readString(saved.path().resolve(PrismodPackLoader.MANIFEST_FILE))).getAsJsonObject().get("namespace").getAsString());
        Path conflict = root.resolveSibling(root.getFileName() + "-three");
        Files.createDirectories(conflict);
        ResourcePackEditorService.SaveResult second = ResourcePackEditorService.save(session, "Updated", "three", Map.of());
        assertFalse(second.success());
    }

    @Test
    void rejectsMalformedFilterJson() throws Exception {
        Path root = pack("example", "gray");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest("example", "gray")).getAsJsonObject());
        ResourcePackEditorService.Session session = ResourcePackEditorService.open(new PrismodPackLoader.PackCandidate(root, metadata));
        ResourcePackEditorService.SaveResult result = ResourcePackEditorService.save(session, "Example", "example",
                Map.of("assets/example/filters/gray/filter.json", "{broken"));
        assertFalse(result.success());
        assertTrue(result.message().contains("校验") || result.message().contains("JSON"));
    }

    private Path pack(String namespace, String id) throws Exception {
        Path root = Files.createDirectories(temp.resolve(namespace));
        Files.writeString(root.resolve(PrismodPackLoader.MANIFEST_FILE), manifest(namespace, id));
        Path filter = Files.createDirectories(root.resolve("assets/" + namespace + "/filters/" + id));
        Files.writeString(filter.resolve("filter.json"), filter());
        return root;
    }

    private static String manifest(String namespace, String id) {
        return "{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"" + namespace + "\",\"filters\":[{\"id\":\"" + id + "\",\"path\":\"assets/" + namespace + "/filters/" + id + "\"}]}";
    }

    private static String filter() {
        return "{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"post_chain\",\"display_name\":\"filter.example.gray\",\"source\":\"post.json\"}";
    }

    private static void put(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
