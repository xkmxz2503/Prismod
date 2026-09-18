package com.xkmxz.prismod.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrismodPackLoaderTest {
    @TempDir
    Path temp;

    @Test
    void scansDirectoriesAndZipFilesInFileNameOrder() throws IOException {
        createDirectoryPack("z-last", "zeta");
        createDirectoryPack("a-first", "alpha");
        createZipPack("m-middle.zip", "middle");

        List<PrismodPackLoader.PackCandidate> candidates = PrismodPackLoader.scan(temp);

        assertEquals(List.of("a-first", "m-middle.zip", "z-last"),
                candidates.stream().map(candidate -> candidate.path().getFileName().toString()).toList());
    }

    @Test
    void skipsMissingMetadataAndInvalidNamespaces() throws IOException {
        Files.createDirectories(temp.resolve("missing"));
        createDirectoryPack("invalid", "Minecraft");
        Files.writeString(temp.resolve("broken.zip"), "not a zip");

        assertEquals(List.of(), PrismodPackLoader.scan(temp));
    }

    @Test
    void rejectsMalformedMetadata() {
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseMetadata(
                JsonParser.parseString("{\"namespace\":\"bad namespace\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseMetadata(
                JsonParser.parseString("{\"namespace\":\"minecraft\"}").getAsJsonObject()));
    }

    @Test
    void parsesOptionalDisplayName() {
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseMetadata(
                JsonParser.parseString("{\"namespace\":\"example\",\"name\":\"Example Filters\"}")
                        .getAsJsonObject());
        assertEquals("Example Filters", metadata.name());
        assertEquals("Example Filters", metadata.displayName("fallback"));
    }

    @Test
    void blankDisplayNameFallsBackToFileName() {
        PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseMetadata(
                JsonParser.parseString("{\"namespace\":\"example\",\"name\":\"  \"}")
                        .getAsJsonObject());
        assertEquals("fallback", metadata.displayName("fallback"));
    }

    @Test
    void rejectsDuplicateNamespacesAfterTheFirstFileName() throws IOException {
        createDirectoryPack("a-first", "same");
        createDirectoryPack("z-second", "same");

        List<PrismodPackLoader.PackCandidate> candidates = PrismodPackLoader.scan(temp);

        assertEquals(List.of("a-first"), candidates.stream()
                .map(candidate -> candidate.path().getFileName().toString()).toList());
    }

    @Test
    void acceptsUnrelatedFilesAndUsesMetadataNamespace() throws IOException {
        String namespace = "custom_pack";
        Path directoryPack = temp.resolve("directory-pack");
        Files.createDirectories(directoryPack.resolve(PrismodPackLoader.ASSETS_DIRECTORY).resolve(namespace));
        Files.createDirectories(directoryPack.resolve("docs/nested"));
        Files.writeString(directoryPack.resolve(PrismodPackLoader.META_FILE),
                "{\"namespace\":\"" + namespace + "\"}");
        Files.writeString(directoryPack.resolve("README.md"), "not read");
        Files.writeString(directoryPack.resolve("docs/nested/notes.txt"), "not read");
        Files.writeString(directoryPack.resolve(PrismodPackLoader.ASSETS_DIRECTORY)
                .resolve(namespace).resolve("filter.json"), "{}");

        PrismodPackLoader.validateContents(directoryPack, namespace);

        Path zipPack = temp.resolve("zip-pack.zip");
        try (OutputStream output = Files.newOutputStream(zipPack);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            putZipEntry(zip, PrismodPackLoader.META_FILE, "{\"namespace\":\"" + namespace + "\"}");
            putZipEntry(zip, "README.md", "not read");
            putZipEntry(zip, "docs/notes.txt", "not read");
            putZipEntry(zip, PrismodPackLoader.ASSETS_DIRECTORY + "/" + namespace + "/filter.json", "{}");
        }

        PrismodPackLoader.validateContents(zipPack, namespace);
    }

    @Test
    void ignoresResourcesFromAnotherNamespace() throws IOException {
        Path directoryPack = temp.resolve("extra-namespace-resource");
        Files.createDirectories(directoryPack.resolve(PrismodPackLoader.ASSETS_DIRECTORY).resolve("other_namespace"));
        Files.writeString(directoryPack.resolve(PrismodPackLoader.META_FILE), "{\"namespace\":\"custom_pack\"}");
        Files.writeString(directoryPack.resolve(PrismodPackLoader.ASSETS_DIRECTORY)
                .resolve("other_namespace").resolve("filter.json"), "{}");

        PrismodPackLoader.validateContents(directoryPack, "custom_pack");
    }

    private static void putZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void createDirectoryPack(String name, String namespace) throws IOException {
        Path pack = Files.createDirectories(temp.resolve(name));
        Files.writeString(pack.resolve(PrismodPackLoader.META_FILE),
                "{\"namespace\":\"" + namespace + "\"}");
    }

    private void createZipPack(String name, String namespace) throws IOException {
        Path zipPath = temp.resolve(name);
        try (OutputStream output = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(PrismodPackLoader.META_FILE));
            zip.write(("{\"namespace\":\"" + namespace + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
