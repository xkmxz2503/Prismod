package com.xkmxz.prismod.client.pack;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
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

    @Test
    void createsRunnablePostChainTemplateWithDebugUniforms() throws Exception {
        Path root = pack("example", "gray");
        String manifest = manifest("example", "gray");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest).getAsJsonObject());
        ResourcePackEditorService.Session session = ResourcePackEditorService.open(new PrismodPackLoader.PackCandidate(root, metadata));
        ResourcePackEditorService.FilterCreation creation = ResourcePackEditorService.createFilter(session,
                new ResourcePackEditorService.FilterCreateRequest("cinematic", "post_chain", 1.0F,
                        Map.of("en_us", "Cinematic"), Map.of()));
        assertTrue(creation.files().containsKey("assets/example/filters/cinematic/post.json"));
        JsonObject post = JsonParser.parseString(creation.files().get("assets/example/filters/cinematic/post.json")).getAsJsonObject();
        assertFalse(post.getAsJsonArray("passes").isEmpty());
        String all = creation.files().values().stream().reduce("", String::concat);
        for (String uniform : List.of("Intensity", "Exposure", "Contrast", "Highlights", "Shadows", "Saturation", "Temperature", "Tint", "Gamma")) {
            assertTrue(all.contains(uniform), uniform);
        }
        assertTrue(creation.files().get("assets/example/filters/cinematic/program/cinematic.fsh").contains("\n"));
        assertFalse(creation.files().get("assets/example/filters/cinematic/program/cinematic.fsh").contains("\\n"));
    }

    @Test
    void creates32CubeAndReadsImportedFilterDirectory() throws Exception {
        Path root = pack("example", "gray");
        String manifest = manifest("example", "gray");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest).getAsJsonObject());
        PrismodPackLoader.PackCandidate candidate = new PrismodPackLoader.PackCandidate(root, metadata);
        ResourcePackEditorService.Session session = ResourcePackEditorService.open(candidate);
        ResourcePackEditorService.FilterCreation creation = ResourcePackEditorService.createFilter(session,
                new ResourcePackEditorService.FilterCreateRequest("identity", "lut3d", 1.0F,
                        Map.of("zh_cn", "测试 LUT"), Map.of()));
        String cube = creation.files().get("assets/example/filters/identity/lut.cube");
        assertTrue(cube.startsWith("LUT_3D_SIZE 32"));
        assertEquals(32768, cube.lines().filter(line -> line.trim().matches("[-+]?\\d.*\\s+[-+]?\\d.*\\s+[-+]?\\d.*")).count());

        Path imported = Files.createDirectories(temp.resolve("imported-filter"));
        Files.writeString(imported.resolve("filter.json"), "{}");
        Files.createDirectories(imported.resolve("program"));
        Files.writeString(imported.resolve("program/example.fsh"), "shader");
        Map<String, String> files = ResourcePackEditorService.readImportedFiles(List.of(imported));
        assertTrue(files.containsKey("filter.json"));
        assertTrue(files.containsKey("program/example.fsh"));

        Path single = temp.resolve("external.cube");
        Files.writeString(single, "LUT_3D_SIZE 2\n0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1\n");
        assertTrue(ResourcePackEditorService.readImportedFiles(List.of(single)).containsKey("lut.cube"));
    }

    @Test
    void addingFilterToDraftUpdatesManifestLanguageAndFilesTogether() throws Exception {
        Path root = pack("example", "gray");
        String manifest = manifest("example", "gray");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest).getAsJsonObject());
        ResourcePackEditorDraft draft = ResourcePackEditorDraft.open(new PrismodPackLoader.PackCandidate(root, metadata));
        draft.addFilter(new ResourcePackEditorService.FilterCreateRequest("new_filter", "lut3d", 0.75F,
                Map.of("en_us", "New Filter", "zh_cn", "新滤镜"), Map.of()));
        assertTrue(draft.filters().stream().anyMatch(filter -> filter.id().equals("new_filter")));
        assertTrue(draft.files().containsKey("assets/example/filters/new_filter/lut.cube"));
        assertTrue(draft.files().get("assets/example/lang/zh_cn.json").contains("新滤镜"));
        assertTrue(draft.files().get(PrismodPackLoader.MANIFEST_FILE).contains("new_filter"));
    }

    @Test
    void createsChineseAndEnglishLanguageTemplatesInDraftOnly() throws Exception {
        Path root = pack("example", "gray");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(
                JsonParser.parseString(manifest("example", "gray")).getAsJsonObject());
        ResourcePackEditorDraft draft = ResourcePackEditorDraft.open(
                new PrismodPackLoader.PackCandidate(root, metadata));
        assertTrue(draft.createLanguageFile("zh_cn"));
        assertTrue(draft.createLanguageFile("en_us"));
        assertFalse(draft.createLanguageFile("zh_cn"));
        assertFalse(draft.createLanguageFile("ja_jp"));
        assertEquals("{}", JsonParser.parseString(draft.files()
                .get("assets/example/lang/zh_cn.json")).toString());
        assertEquals("{}", JsonParser.parseString(draft.files()
                .get("assets/example/lang/en_us.json")).toString());
        assertFalse(Files.exists(root.resolve("assets/example/lang/zh_cn.json")));
    }

    @Test
    void savesVariableSizeLutWithinSupportedRange() throws Exception {
        Path root = Files.createDirectories(temp.resolve("lut-pack"));
        String manifest = "{\"schema\":\"prismod.resource_pack\",\"format_version\":1,\"namespace\":\"example\",\"filters\":[{\"id\":\"small\",\"path\":\"assets/example/filters/small\"}]}";
        Files.writeString(root.resolve(PrismodPackLoader.MANIFEST_FILE), manifest);
        Path filter = Files.createDirectories(root.resolve("assets/example/filters/small"));
        Files.writeString(filter.resolve("filter.json"), "{\"schema\":\"prismod.filter\",\"format_version\":1,\"type\":\"lut3d\",\"display_name\":\"filter.example.small\",\"source\":\"lut.cube\",\"color_space\":\"sRGB\"}");
        Files.writeString(filter.resolve("lut.cube"), "LUT_3D_SIZE 2\n0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1\n");
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(JsonParser.parseString(manifest).getAsJsonObject());
        ResourcePackEditorService.Session session = ResourcePackEditorService.open(new PrismodPackLoader.PackCandidate(root, metadata));
        ResourcePackEditorService.SaveResult result = ResourcePackEditorService.save(session, "LUT Pack", "example", Map.of());
        assertTrue(result.success(), result.message());
    }

    @Test
    void createsEmptyPackSkeletonWithOptionalReadme() throws Exception {
        Path packs = Files.createDirectories(temp.resolve("resourcepacks"));
        ResourcePackEditorService.CreatePackResult result = ResourcePackEditorService.createPack(
                packs, new ResourcePackEditorService.CreatePackRequest("Example Pack", "newpack", true));
        assertTrue(result.success(), result.message());
        assertEquals(packs.resolve("newpack"), result.path());
        JsonObject manifest = JsonParser.parseString(Files.readString(result.path().resolve(PrismodPackLoader.MANIFEST_FILE))).getAsJsonObject();
        assertEquals("Example Pack", manifest.get("name").getAsString());
        assertTrue(manifest.getAsJsonArray("filters").isEmpty());
        assertTrue(Files.exists(result.path().resolve("README.md")));
        assertTrue(PrismodPackLoader.scan(packs).stream().anyMatch(candidate ->
                candidate.metadata().namespace().equals("newpack")));
    }

    @Test
    void createsPackWithoutNameOrReadmeWhenDisabled() throws Exception {
        Path packs = Files.createDirectories(temp.resolve("resourcepacks"));
        ResourcePackEditorService.CreatePackResult result = ResourcePackEditorService.createPack(
                packs, new ResourcePackEditorService.CreatePackRequest("", "empty_pack", false));
        assertTrue(result.success(), result.message());
        JsonObject manifest = JsonParser.parseString(Files.readString(result.path().resolve(PrismodPackLoader.MANIFEST_FILE))).getAsJsonObject();
        assertFalse(manifest.has("name"));
        assertFalse(Files.exists(result.path().resolve("README.md")));
        try (var paths = Files.walk(result.path())) {
            assertTrue(paths.noneMatch(path -> path.toString().contains("filters") || path.toString().endsWith(".fsh")));
        }
    }

    @Test
    void rejectsReservedInvalidDuplicateAndExistingPackDirectories() throws Exception {
        Path packs = Files.createDirectories(temp.resolve("resourcepacks"));
        assertFalse(ResourcePackEditorService.createPack(packs,
                new ResourcePackEditorService.CreatePackRequest("", "minecraft", false)).success());
        assertFalse(ResourcePackEditorService.createPack(packs,
                new ResourcePackEditorService.CreatePackRequest("", "bad namespace", false)).success());
        assertTrue(ResourcePackEditorService.createPack(packs,
                new ResourcePackEditorService.CreatePackRequest("", "duplicate", false)).success());
        assertFalse(ResourcePackEditorService.createPack(packs,
                new ResourcePackEditorService.CreatePackRequest("", "duplicate", false)).success());
        Path existing = Files.createDirectories(packs.resolve("existing"));
        Files.writeString(existing.resolve("keep.txt"), "keep");
        ResourcePackEditorService.CreatePackResult conflict = ResourcePackEditorService.createPack(packs,
                new ResourcePackEditorService.CreatePackRequest("", "existing", false));
        assertFalse(conflict.success());
        assertEquals("keep", Files.readString(existing.resolve("keep.txt")));
    }

    @Test
    void failedCreationLeavesNoTemporaryDirectory() throws Exception {
        Path packs = Files.createDirectories(temp.resolve("resourcepacks"));
        ResourcePackEditorService.CreatePackResult result = ResourcePackEditorService.createPack(
                packs, new ResourcePackEditorService.CreatePackRequest("", "minecraft", true));
        assertFalse(result.success());
        try (var paths = Files.list(packs)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().contains("prismod-create")));
        }
    }

    private Path pack(String namespace, String id) throws Exception {
        Path root = Files.createDirectories(temp.resolve(namespace));
        Files.writeString(root.resolve(PrismodPackLoader.MANIFEST_FILE), manifest(namespace, id));
        Path filter = Files.createDirectories(root.resolve("assets/" + namespace + "/filters/" + id));
        Files.writeString(filter.resolve("filter.json"), filter());
        Files.writeString(filter.resolve("post.json"), "{\"targets\":[],\"passes\":[]}");
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
