package com.xkmxz.prismod.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.resource.DelegatingPackResources;
import net.minecraftforge.resource.PathPackResources;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Discovers Prismod filter packs from config/prismod/resourcepacks. */
public final class PrismodPackLoader implements RepositorySource {
    public static final PrismodPackLoader INSTANCE = new PrismodPackLoader();
    public static final String PACK_ID = "prismod_external_resources";
    public static final String CONFIG_FILE = "prismod/config/prismod-client.toml";
    public static final String META_FILE = "prismod.meta.json";
    private static final String RESOURCE_PACKS_DIRECTORY = "prismod/resourcepacks";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> RESERVED_NAMESPACES = Set.of("minecraft", "prismod");

    private PrismodPackLoader() {
    }

    public static Path resourcePacksDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(RESOURCE_PACKS_DIRECTORY);
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(resourcePacksDirectory());
            Files.createDirectories(FMLPaths.CONFIGDIR.get().resolve("prismod/config"));
        } catch (IOException exception) {
            LOGGER.warn("Unable to create Prismod configuration directories", exception);
        }
    }

    public static boolean isPrismodPackId(String packId) {
        return PACK_ID.equals(packId);
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        ensureDirectories();
        List<PackCandidate> candidates = scan(resourcePacksDirectory());
        if (candidates.isEmpty()) {
            return;
        }

        List<PackResources> resources = new ArrayList<>();
        for (PackCandidate candidate : candidates) {
            try {
                resources.add(createResources(candidate));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping Prismod resource pack {}", candidate.path().getFileName(), exception);
            }
        }
        if (resources.isEmpty()) {
            return;
        }

        // DelegatingPackResources returns the first matching delegate. Reverse the
        // ascending scan order so a later file name has stable override precedence.
        List<PackResources> delegates = new ArrayList<>(resources);
        java.util.Collections.reverse(delegates);
        Pack pack = Pack.readMetaAndCreate(PACK_ID, Component.literal("Prismod custom filters"), true,
                id -> new DelegatingPackResources(id, false,
                        new PackMetadataSection(Component.literal("Prismod custom filters"),
                                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)),
                        delegates), PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM, PackSource.BUILT_IN);
        if (pack != null) {
            onLoad.accept(pack);
        }
    }

    static List<PackCandidate> scan(Path directory) {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) || (Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))) {
                    entries.add(path);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to scan Prismod resource pack directory {}", directory, exception);
            return List.of();
        }
        entries.sort(Comparator.comparing(path -> path.getFileName().toString()));

        List<PackCandidate> result = new ArrayList<>();
        for (Path path : entries) {
            try {
                PackMetadata metadata = Files.isDirectory(path) ? readDirectoryMetadata(path) : readZipMetadata(path);
                if (metadata != null && dependenciesMatch(metadata.dependencies())) {
                    result.add(new PackCandidate(path, metadata));
                    LOGGER.info("Found Prismod resource pack {} (namespace {})", path.getFileName(), metadata.namespace());
                }
            } catch (Exception exception) {
                LOGGER.warn("Skipping invalid Prismod resource pack {}", path.getFileName(), exception);
            }
        }
        return List.copyOf(result);
    }

    static PackMetadata parseMetadata(JsonObject object) {
        if (object == null || !object.has("namespace") || !object.get("namespace").isJsonPrimitive()
                || !object.getAsJsonPrimitive("namespace").isString()) {
            throw new IllegalArgumentException("missing namespace");
        }
        String namespace = object.get("namespace").getAsString();
        if (!ResourceLocation.isValidNamespace(namespace) || RESERVED_NAMESPACES.contains(namespace)) {
            throw new IllegalArgumentException("invalid or reserved namespace: " + namespace);
        }

        Map<String, String> dependencies = new HashMap<>();
        if (object.has("dependencies")) {
            if (!object.get("dependencies").isJsonObject()) {
                throw new IllegalArgumentException("dependencies must be an object");
            }
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("dependencies").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()
                        || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("invalid dependency entry");
                }
                dependencies.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return new PackMetadata(namespace, Map.copyOf(dependencies));
    }

    private static PackMetadata readDirectoryMetadata(Path path) throws IOException {
        Path metadataPath = path.resolve(META_FILE);
        if (!Files.isRegularFile(metadataPath)) {
            throw new IOException("missing " + META_FILE);
        }
        try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            return parseMetadata(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    private static PackMetadata readZipMetadata(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry(META_FILE);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("missing " + META_FILE);
            }
            try (InputStream stream = zip.getInputStream(entry);
                 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parseMetadata(JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    private static boolean dependenciesMatch(Map<String, String> dependencies)
            throws InvalidVersionSpecificationException {
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            VersionRange range = VersionRange.createFromVersionSpec(entry.getValue());
            ArtifactVersion version = ModList.get().getModContainerById(entry.getKey())
                    .map(mod -> mod.getModInfo().getVersion()).orElse(null);
            if (version == null || !range.containsVersion(version)) {
                LOGGER.warn("Prismod resource pack dependency {} {} is not satisfied", entry.getKey(), entry.getValue());
                return false;
            }
        }
        return true;
    }

    private static PackResources createResources(PackCandidate candidate) {
        Path path = candidate.path();
        if (Files.isDirectory(path)) {
            return new PathPackResources(candidate.fileName(), false, path);
        }
        SecureJar secureJar = SecureJar.from(path);
        return new PathPackResources(candidate.fileName(), false, path) {
            @Override
            protected Path resolve(String... paths) {
                if (paths.length == 0) {
                    throw new IllegalArgumentException("Missing path");
                }
                return secureJar.getPath(String.join("/", paths));
            }
        };
    }

    record PackCandidate(Path path, PackMetadata metadata) {
        String fileName() {
            return path.getFileName().toString();
        }
    }

    record PackMetadata(String namespace, Map<String, String> dependencies) {
    }
}
