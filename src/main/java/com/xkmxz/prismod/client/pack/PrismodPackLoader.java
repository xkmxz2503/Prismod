package com.xkmxz.prismod.client.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.config.PrismodClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
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
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Discovers Prismod filter packs from config/prismod/resourcepacks. */
public final class PrismodPackLoader {
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

    private static PrismodResourceManager activeResources;

    /** Rebuilds Prismod's private resource view without changing Minecraft's pack repository. */
    public static synchronized PrismodResourceManager reload(ResourceManager vanilla) {
        ensureDirectories();
        if (activeResources != null) activeResources.close();
        activeResources = new PrismodResourceManager(vanilla, scan(resourcePacksDirectory()));
        return activeResources;
    }

    public static synchronized PrismodResourceManager resources(ResourceManager vanilla) {
        return activeResources == null ? reload(vanilla) : activeResources;
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

    private static final class PrismodPackResources extends AbstractPackResources {
        private final Path source;
        private final String namespace;

        private PrismodPackResources(PackCandidate candidate) {
            super(packIdForNamespace(candidate.metadata().namespace()), false);
            this.source = candidate.path();
            this.namespace = candidate.metadata().namespace();
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... paths) {
            if (paths.length == 0) return null;
            String relative = String.join("/", paths);
            if (Files.isDirectory(source)) {
                Path file = source.resolve(relative).normalize();
                return file.startsWith(source) && Files.isRegularFile(file) ? IoSupplier.create(file) : null;
            }
            return zipSupplier(relative);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            if (type != PackType.CLIENT_RESOURCES || !namespace.equals(id.getNamespace())) return null;
            String relative = "assets/" + namespace + "/" + id.getPath();
            if (Files.isDirectory(source)) {
                Path file = source.resolve(relative).normalize();
                return file.startsWith(source) && Files.isRegularFile(file) ? IoSupplier.create(file) : null;
            }
            return zipSupplier(relative);
        }

        @Override
        public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
            if (type != PackType.CLIENT_RESOURCES || !this.namespace.equals(namespace)) return;
            String relativePrefix = "assets/" + namespace + "/" + prefix;
            if (Files.isDirectory(source)) {
                Path root = source.resolve(relativePrefix).normalize();
                if (!root.startsWith(source) || !Files.isDirectory(root)) return;
                try (var paths = Files.walk(root)) {
                    paths.filter(Files::isRegularFile).forEach(path -> {
                        String relative = source.relativize(path).toString().replace('\\', '/');
                        output.accept(ResourceLocation.fromNamespaceAndPath(namespace,
                                relative.substring(("assets/" + namespace + "/").length())), IoSupplier.create(path));
                    });
                } catch (IOException exception) {
                    LOGGER.warn("Unable to list Prismod resources in {}", source, exception);
                }
                return;
            }
            try (ZipFile zip = new ZipFile(source.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(relativePrefix + "/")) continue;
                    String path = entry.getName().substring(("assets/" + namespace + "/").length());
                    output.accept(ResourceLocation.fromNamespaceAndPath(namespace, path), zipSupplier(entry.getName()));
                }
            } catch (IOException exception) {
                LOGGER.warn("Unable to list Prismod resources in {}", source, exception);
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? Set.of(namespace) : Set.of();
        }

        @Override
        public void close() {
        }

        private IoSupplier<InputStream> zipSupplier(String entryName) {
            try (ZipFile zip = new ZipFile(source.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null || entry.isDirectory()) return null;
            } catch (IOException exception) {
                return null;
            }
            return () -> {
                ZipFile zip = new ZipFile(source.toFile());
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null || entry.isDirectory()) {
                    zip.close();
                    throw new IOException("Missing ZIP entry " + entryName);
                }
                InputStream stream = zip.getInputStream(entry);
                return new FilterInputStream(stream) {
                    @Override
                    public void close() throws IOException {
                        try { super.close(); } finally { zip.close(); }
                    }
                };
            };
        }
    }

    public static final class PrismodResourceManager implements ResourceManager, AutoCloseable {
        private final ResourceManager vanilla;
        private final List<PrismodPackResources> packs;

        PrismodResourceManager(ResourceManager vanilla, List<PackCandidate> candidates) {
            this.vanilla = vanilla;
            this.packs = candidates.stream().filter(candidate -> PrismodClientConfig.isPackEnabled(
                    candidate.metadata().namespace())).map(PrismodPackResources::new).toList();
        }

        @Override
        public Set<String> getNamespaces() {
            Set<String> namespaces = new HashSet<>(vanilla.getNamespaces());
            packs.forEach(pack -> namespaces.addAll(pack.getNamespaces(PackType.CLIENT_RESOURCES)));
            return Set.copyOf(namespaces);
        }

        @Override
        public java.util.Optional<Resource> getResource(ResourceLocation id) {
            for (PrismodPackResources pack : packs) {
                IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, id);
                if (supplier != null) return java.util.Optional.of(new Resource(pack, supplier));
            }
            return vanilla.getResource(id);
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation id) {
            List<Resource> result = new ArrayList<>();
            for (PrismodPackResources pack : packs) {
                IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, id);
                if (supplier != null) result.add(new Resource(pack, supplier));
            }
            result.addAll(vanilla.getResourceStack(id));
            return List.copyOf(result);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String prefix, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, Resource> result = new HashMap<>(vanilla.listResources(prefix, filter));
            for (PrismodPackResources pack : packs) {
                pack.listResources(PackType.CLIENT_RESOURCES, pack.namespace, prefix, (id, supplier) -> {
                    if (filter.test(id)) result.put(id, new Resource(pack, supplier));
                });
            }
            return Map.copyOf(result);
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String prefix,
                                                                         Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, List<Resource>> result = new HashMap<>();
            vanilla.listResourceStacks(prefix, filter).forEach((id, resources) ->
                    result.put(id, new ArrayList<>(resources)));
            for (PrismodPackResources pack : packs) {
                pack.listResources(PackType.CLIENT_RESOURCES, pack.namespace, prefix, (id, supplier) -> {
                    if (filter.test(id)) result.computeIfAbsent(id, ignored -> new ArrayList<>())
                            .add(new Resource(pack, supplier));
                });
            }
            return result.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.concat(vanilla.listPacks(), packs.stream().map(pack -> pack));
        }

        @Override
        public void close() {
            packs.forEach(PrismodPackResources::close);
        }
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
