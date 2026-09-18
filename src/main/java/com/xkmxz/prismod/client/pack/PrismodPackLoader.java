package com.xkmxz.prismod.client.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.config.PrismodClientConfig;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    public static final String PACK_ID_PREFIX = "prismod_external_";
    /** Legacy aggregate ID retained for source compatibility with older integrations. */
    @Deprecated
    public static final String PACK_ID = PACK_ID_PREFIX + "resources";
    public static final String CONFIG_FILE = "prismod/config/prismod-client.toml";
    public static final String META_FILE = "prismod.meta.json";
    static final String ASSETS_DIRECTORY = "assets";
    private static final String RESOURCE_PACKS_DIRECTORY = "prismod/resourcepacks";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> RESERVED_NAMESPACES = Set.of("minecraft", "prismod");
    private static boolean configWasUnavailable;

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
        return packId != null && packId.startsWith(PACK_ID_PREFIX)
                && packId.length() > PACK_ID_PREFIX.length();
    }

    public static String packIdForNamespace(String namespace) {
        return PACK_ID_PREFIX + namespace;
    }

    public static String namespaceForPackId(String packId) {
        return isPrismodPackId(packId) ? packId.substring(PACK_ID_PREFIX.length()) : null;
    }

    public static boolean configWasUnavailable() {
        return configWasUnavailable;
    }

    public static void clearConfigUnavailable() {
        configWasUnavailable = false;
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        ensureDirectories();
        List<PackCandidate> candidates = scan(resourcePacksDirectory());
        for (PackCandidate candidate : candidates) {
            if (!PrismodClientConfig.isLoaded()) {
                configWasUnavailable = true;
            } else if (!PrismodClientConfig.isPackEnabled(candidate.metadata().namespace())) {
                continue;
            }
            try {
                Pack pack = Pack.readMetaAndCreate(packIdForNamespace(candidate.metadata().namespace()),
                        Component.literal(candidate.metadata().displayName(candidate.fileName())), true,
                        id -> new DelegatingPackResources(id, false,
                                new PackMetadataSection(Component.literal(candidate.metadata().displayName(candidate.fileName())),
                                        SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)),
                                List.of(createResources(candidate))), PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM,
                        PackSource.BUILT_IN);
                if (pack != null) onLoad.accept(pack);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping Prismod resource pack {}", candidate.path().getFileName(), exception);
            }
        }
    }

    public static List<PackCandidate> scan(Path directory) {
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
        Set<String> namespaces = new HashSet<>();
        for (Path path : entries) {
            try {
                PackMetadata metadata = Files.isDirectory(path) ? readDirectoryMetadata(path) : readZipMetadata(path);
                if (metadata != null && dependenciesMatch(metadata.dependencies())) {
                    if (!namespaces.add(metadata.namespace())) {
                        LOGGER.warn("Skipping Prismod resource pack {} because namespace {} is already used",
                                path.getFileName(), metadata.namespace());
                        continue;
                    }
                    result.add(new PackCandidate(path, metadata));
                    LOGGER.info("Found Prismod resource pack {} (namespace {})", path.getFileName(), metadata.namespace());
                }
            } catch (Exception exception) {
                LOGGER.warn("Skipping invalid Prismod resource pack {}", path.getFileName(), exception);
            }
        }
        return List.copyOf(result);
    }

    public static PackMetadata parseMetadata(JsonObject object) {
        if (object == null || !object.has("namespace") || !object.get("namespace").isJsonPrimitive()
                || !object.getAsJsonPrimitive("namespace").isString()) {
            throw new IllegalArgumentException("missing namespace");
        }
        String namespace = object.get("namespace").getAsString();
        if (!ResourceLocation.isValidNamespace(namespace) || RESERVED_NAMESPACES.contains(namespace)) {
            throw new IllegalArgumentException("invalid or reserved namespace: " + namespace);
        }

        String name = null;
        if (object.has("name")) {
            if (!object.get("name").isJsonPrimitive() || !object.getAsJsonPrimitive("name").isString()) {
                throw new IllegalArgumentException("name must be a string");
            }
            name = object.get("name").getAsString().trim();
            if (name.isBlank()) name = null;
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
        return new PackMetadata(namespace, name, Map.copyOf(dependencies));
    }

    public static ImportResult importPack(Path source) {
        if (source == null || (!Files.isDirectory(source) && !isZip(source))) {
            return ImportResult.failure("请选择资源包目录或 ZIP 文件。");
        }
        try {
            PackMetadata metadata = Files.isDirectory(source) ? readDirectoryMetadata(source) : readZipMetadata(source);
            if (!dependenciesMatch(metadata.dependencies())) {
                return ImportResult.failure("资源包依赖不满足，未导入。");
            }
            validateContents(source, metadata.namespace());
            Path directory = resourcePacksDirectory();
            Files.createDirectories(directory);
            for (PackCandidate candidate : scan(directory)) {
                if (candidate.metadata().namespace().equals(metadata.namespace())) {
                    return ImportResult.failure("资源包 namespace 已存在，未导入。");
                }
            }
            Path target = directory.resolve(source.getFileName().toString()).normalize();
            if (!target.getParent().equals(directory) || Files.exists(target)) {
                return ImportResult.failure("目标目录已有同名资源包，未覆盖原文件。");
            }
            if (Files.isDirectory(source)) copyDirectory(source, target);
            else Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            return ImportResult.success(new PackCandidate(target, metadata));
        } catch (Exception exception) {
            LOGGER.warn("Unable to import Prismod resource pack {}", source, exception);
            return ImportResult.failure("资源包校验失败：" + safeMessage(exception));
        }
    }

    private static boolean isZip(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    static void validateContents(Path source, String namespace) throws IOException {
        String prefix = ASSETS_DIRECTORY + "/" + namespace + "/";
        if (Files.isDirectory(source)) {
            Path assetsRoot = source.resolve(ASSETS_DIRECTORY).resolve(namespace).normalize();
            if (!Files.isDirectory(assetsRoot) || !assetsRoot.startsWith(source.normalize())) return;
            try (var paths = Files.walk(assetsRoot)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    validateEntry(source.relativize(path).toString().replace('\\', '/'));
                }
            }
            return;
        }
        try (ZipFile zip = new ZipFile(source.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    validateEntry(entry.getName());
                }
            }
        }
    }

    private static void validateEntry(String entry) throws IOException {
        String normalized = entry.replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/")) {
            throw new IOException("资源路径无效：" + entry);
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                throw new IOException("资源路径不能包含 ..：" + entry);
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try {
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    Path destination = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) Files.createDirectories(destination);
                    else Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException exception) {
            deleteRecursively(target);
            throw exception;
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(child);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
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

    public record PackCandidate(Path path, PackMetadata metadata) {
        public String fileName() {
            return path.getFileName().toString();
        }
    }

    public record PackMetadata(String namespace, String name, Map<String, String> dependencies) {
        public String displayName(String fallback) {
            return name == null || name.isBlank() ? fallback : name;
        }
    }

    public record ImportResult(boolean success, String message, PackCandidate candidate) {
        static ImportResult success(PackCandidate candidate) {
            return new ImportResult(true, "资源包已导入。", candidate);
        }

        static ImportResult failure(String message) {
            return new ImportResult(false, message, null);
        }
    }
}
