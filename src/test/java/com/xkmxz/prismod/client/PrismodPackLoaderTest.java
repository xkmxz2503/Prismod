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
