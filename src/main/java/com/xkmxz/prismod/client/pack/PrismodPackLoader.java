package com.xkmxz.prismod.client.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.config.PrismodClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

import java.io.FilterInputStream;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Prismod v1 私有资源包的清单解析与受限资源视图。 */
public final class PrismodPackLoader {
    public static final String PACK_ID_PREFIX = "prismod_external_";
    @Deprecated public static final String PACK_ID = PACK_ID_PREFIX + "resources";
    public static final String CONFIG_FILE = "prismod/config/prismod-client.toml";
    public static final String MANIFEST_FILE = "prismod.pack.json";
    @Deprecated public static final String META_FILE = "prismod.meta.json";
    static final String ASSETS_DIRECTORY = "assets";
    private static final int FORMAT_VERSION = 1;
    private static final String RESOURCE_PACKS_DIRECTORY = "prismod/resourcepacks";
    private static final String BACKUP_DIRECTORY_NAME = ".prismod-backup";
    private static final String LANGUAGE_DIRECTORY = "assets/%s/lang/";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> RESERVED_NAMESPACES = Set.of("minecraft", "prismod");
    /** 配置目录中的默认包，目录本身就是一个完整的 Prismod v1 资源包。 */
    private static final String BUNDLED_DIRECTORY = "prismod/builtin/prismod_default_filters";
    /** 模组 jar 中的 TACZ 风格自包含默认包。 */
    private static final String BUNDLED_SOURCE_DIRECTORY = "assets/prismod/custom/prismod_default_filters";
    private static PrismodResourceManager activeResources;

    private PrismodPackLoader() { }

    public static Path resourcePacksDirectory() { return FMLPaths.CONFIGDIR.get().resolve(RESOURCE_PACKS_DIRECTORY); }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(resourcePacksDirectory());
            Files.createDirectories(FMLPaths.CONFIGDIR.get().resolve("prismod/config"));
            Files.createDirectories(FMLPaths.CONFIGDIR.get().resolve(BUNDLED_DIRECTORY));
        } catch (IOException exception) { LOGGER.warn("Unable to create Prismod configuration directories", exception); }
    }

    public static boolean isPrismodPackId(String packId) {
        return packId != null && packId.startsWith(PACK_ID_PREFIX) && packId.length() > PACK_ID_PREFIX.length();
    }

    public static String packIdForNamespace(String namespace) { return PACK_ID_PREFIX + namespace; }

    public static String namespaceForPackId(String packId) {
        return isPrismodPackId(packId) ? packId.substring(PACK_ID_PREFIX.length()) : null;
    }

    public static synchronized PrismodResourceManager reload(ResourceManager vanilla) {
        ensureDirectories();
        if (activeResources != null) activeResources.close();
        activeResources = new PrismodResourceManager(vanilla, bundledCandidate(), scan(resourcePacksDirectory()));
        return activeResources;
    }

    public static synchronized PrismodResourceManager resources(ResourceManager vanilla) {
        return activeResources == null ? reload(vanilla) : activeResources;
    }

    /** Resolve a filter translation from its owning Prismod resource pack only. */
    public static Optional<String> translate(ResourceLocation filterId, String packNamespace, String translationKey) {
        PrismodResourceManager resources = activeResources;
        if (resources == null || translationKey == null || translationKey.isBlank()) return Optional.empty();
        String namespace = packNamespace == null ? filterId.getNamespace() : packNamespace;
        return resources.translate(namespace, translationKey);
    }

    public static List<PackCandidate> scan(Path directory) {
        return scanInternal(directory, true);
    }

    /**
     * 扫描资源包编辑候选。此扫描只校验清单本身，不要求清单声明的滤镜文件已存在，
     * 这样编辑器可以打开并清理失效声明；正常运行仍使用 {@link #scan(Path)} 的严格扫描。
     */
    public static List<PackCandidate> scanForEditing(Path directory) {
        return scanInternal(directory, false);
    }

    private static List<PackCandidate> scanInternal(Path directory, boolean validateContents) {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (fileName.equals(BACKUP_DIRECTORY_NAME) || fileName.endsWith(".prismod-backup")) continue;
                if (Files.isDirectory(path) || (Files.isRegularFile(path)
                        && fileName.toLowerCase(Locale.ROOT).endsWith(".zip"))) entries.add(path);
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
                PackMetadata metadata = Files.isDirectory(path)
                        ? readDirectoryManifest(path, false, validateContents)
                        : readZipManifest(path, validateContents);
                if (!dependenciesMatch(metadata.dependencies())) continue;
                if (!namespaces.add(metadata.namespace())) {
                    LOGGER.warn("Skipping Prismod resource pack {} because namespace {} is already used", path.getFileName(), metadata.namespace());
                    continue;
                }
                result.add(new PackCandidate(path, metadata));
                LOGGER.info("Found Prismod v1 resource pack {} (namespace {})", path.getFileName(), metadata.namespace());
            } catch (Exception exception) { LOGGER.warn("Skipping invalid Prismod v1 resource pack {}", path.getFileName(), exception); }
        }
        return List.copyOf(result);
    }

    public static PackMetadata parseManifest(JsonObject object) {
        return parseManifest(object, false);
    }

    private static PackMetadata parseManifest(JsonObject object, boolean bundled) {
        if (object == null || !"prismod.resource_pack".equals(string(object, "schema"))) {
            throw new IllegalArgumentException("schema must be prismod.resource_pack");
        }
        if (!object.has("format_version") || !object.get("format_version").isJsonPrimitive()
                || object.get("format_version").getAsInt() != FORMAT_VERSION) throw new IllegalArgumentException("unsupported resource pack format_version");
        String namespace = string(object, "namespace");
        if (!ResourceLocation.isValidNamespace(namespace) || (!bundled && RESERVED_NAMESPACES.contains(namespace))) throw new IllegalArgumentException("invalid or reserved namespace: " + namespace);
        String name = optionalString(object, "name");
        Map<String, String> dependencies = dependencies(object);
        if (!object.has("filters") || !object.get("filters").isJsonArray()) throw new IllegalArgumentException("filters must be an array");
        List<PackFilterEntry> filters = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : object.getAsJsonArray("filters")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("filter entry must be an object");
            JsonObject filter = element.getAsJsonObject();
            String id = string(filter, "id");
            if (!ResourceLocation.isValidPath(id) || !ids.add(id)) throw new IllegalArgumentException("invalid or duplicate filter id: " + id);
            filters.add(new PackFilterEntry(id, normalizeFilterDirectory(string(filter, "path"), namespace)));
        }
        return new PackMetadata(namespace, name, Map.copyOf(dependencies), List.copyOf(filters));
    }

    private static PackCandidate bundledCandidate() {
        Path target = FMLPaths.CONFIGDIR.get().resolve(BUNDLED_DIRECTORY);
        try {
            exportBundledPack(target);
            PackMetadata metadata = readDirectoryManifest(target, true);
            if (!"prismod".equals(metadata.namespace())) throw new IOException("bundled namespace must be prismod");
            return new PackCandidate(target, metadata, true);
        } catch (Exception exception) {
            LOGGER.error("Unable to export/load bundled Prismod v1 resource pack", exception);
            return null;
        }
    }

    /**
     * 与 TACZ 默认枪包相同：把模组内 custom 目录中的完整默认包导出到配置目录，之后完全
     * 按目录资源包读取。只补齐不存在的文件，避免重启时覆盖用户对默认滤镜包的编辑。
     */
    private static void exportBundledPack(Path target) throws IOException {
        IModFile modFile = ModList.get().getModContainerById("prismod")
                .map(container -> container.getModInfo().getOwningFile().getFile())
                .orElse(null);
        if (modFile != null) {
            Path sourceRoot = modFile.findResource(BUNDLED_SOURCE_DIRECTORY);
            if (Files.isDirectory(sourceRoot)
                    && Files.isRegularFile(sourceRoot.resolve(MANIFEST_FILE))) {
                copyTreeIfMissing(sourceRoot, target);
                syncBundledRuntime(sourceRoot, target);
                return;
            }
        }

        // 开发环境或测试环境没有可查询的 Forge IModFile 时，仅用 classpath 找到默认包源目录。
        java.net.URL manifestUrl = PrismodPackLoader.class.getResource(
                "/" + BUNDLED_SOURCE_DIRECTORY + "/" + MANIFEST_FILE);
        if (manifestUrl == null || !"file".equalsIgnoreCase(manifestUrl.getProtocol())) {
            throw new IOException("missing bundled Prismod default pack source");
        }
        try {
            Path sourceRoot = java.nio.file.Paths.get(manifestUrl.toURI()).getParent();
            copyTreeIfMissing(sourceRoot, target);
            syncBundledRuntime(sourceRoot, target);
        } catch (java.net.URISyntaxException exception) {
            throw new IOException("invalid bundled resource URL", exception);
        }
    }

    private static void copyTreeIfMissing(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else copyIfMissing(path, destination);
            }
        }
    }

    private static void copyIfMissing(Path source, Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * 运行时处理器属于模组实现，不是用户编辑的滤镜内容。默认包首次导出后仍保留
     * 用户对 filters/lang 等目录的修改，但每次重载都同步 runtime，避免旧版本 shader
     * 或 program 清单继续被配置目录副本遮蔽。
     */
    private static void syncBundledRuntime(Path sourceRoot, Path targetRoot) throws IOException {
        Path source = sourceRoot.resolve("assets/prismod/runtime");
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = targetRoot.resolve(sourceRoot.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.toAbsolutePath().getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    /** @deprecated v1 has no legacy metadata parser; use {@link #parseManifest(JsonObject)}. */
    @Deprecated public static PackMetadata parseMetadata(JsonObject object) {
        return parseManifest(object);
    }

    public static ImportResult importPack(Path source) {
        if (source == null || (!Files.isDirectory(source) && !isZip(source))) return ImportResult.failure("请选择资源包目录或 ZIP 文件。");
        try {
            PackMetadata metadata = Files.isDirectory(source) ? readDirectoryManifest(source) : readZipManifest(source);
            if (!dependenciesMatch(metadata.dependencies())) return ImportResult.failure("资源包依赖不满足，未导入。");
            Path directory = resourcePacksDirectory();
            Files.createDirectories(directory);
            for (PackCandidate candidate : scan(directory)) if (candidate.metadata().namespace().equals(metadata.namespace())) return ImportResult.failure("资源包 namespace 已存在，未导入。");
            Path target = directory.resolve(source.getFileName().toString()).normalize();
            if (!target.getParent().equals(directory) || Files.exists(target)) return ImportResult.failure("目标目录已有同名资源包，未覆盖原文件。");
            if (Files.isDirectory(source)) copyDirectory(source, target); else Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            return ImportResult.success(new PackCandidate(target, metadata));
        } catch (Exception exception) {
            LOGGER.warn("Unable to import Prismod resource pack {}", source, exception);
            return ImportResult.failure("资源包校验失败：" + safeMessage(exception));
        }
    }

    static void validateContents(Path source, PackMetadata metadata) throws IOException {
        for (PackFilterEntry filter : metadata.filters()) if (!contains(source, filter.path() + "/filter.json")) throw new IOException("missing " + filter.path() + "/filter.json");
    }

    /** Compatibility helper for callers migrating from the pre-v1 API; it never validates legacy metadata. */
    @Deprecated
    static void validateContents(Path source, String namespace) throws IOException {
        if (Files.isDirectory(source) && Files.isRegularFile(source.resolve(MANIFEST_FILE))) {
            try (Reader reader = Files.newBufferedReader(source.resolve(MANIFEST_FILE), StandardCharsets.UTF_8)) {
                validateContents(source, parseManifest(JsonParser.parseReader(reader).getAsJsonObject()));
            }
        } else if (!Files.isDirectory(source)) {
            throw new IOException("v1 manifest is required");
        }
    }

    private static boolean contains(Path source, String relative) throws IOException {
        if (Files.isDirectory(source)) {
            Path file = source.resolve(relative).normalize();
            return file.startsWith(source.normalize()) && Files.isRegularFile(file);
        }
        try (ZipFile zip = new ZipFile(source.toFile())) { ZipEntry entry = zip.getEntry(relative); return entry != null && !entry.isDirectory(); }
    }

    private static String normalizeFilterDirectory(String value, String namespace) {
        String normalized = value.replace('\\', '/');
        String prefix = ASSETS_DIRECTORY + "/" + namespace + "/filters/";
        if (!normalized.startsWith(prefix) || normalized.endsWith("/") || normalized.contains("//") || normalized.contains("/./") || normalized.startsWith("/") || normalized.contains("..")) throw new IllegalArgumentException("filter path must stay below " + prefix);
        String relative = normalized.substring(prefix.length());
        if (relative.isBlank() || !ResourceLocation.isValidPath(relative)) throw new IllegalArgumentException("invalid filter path: " + value);
        return normalized;
    }

    private static String string(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive() || !object.getAsJsonPrimitive(field).isString()) throw new IllegalArgumentException("missing " + field);
        String value = object.get(field).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("blank " + field);
        return value;
    }

    private static String optionalString(JsonObject object, String field) {
        if (!object.has(field)) return null;
        if (!object.get(field).isJsonPrimitive() || !object.getAsJsonPrimitive(field).isString()) throw new IllegalArgumentException(field + " must be a string");
        String value = object.get(field).getAsString().trim();
        return value.isBlank() ? null : value;
    }

    private static Map<String, String> dependencies(JsonObject object) {
        Map<String, String> result = new HashMap<>();
        if (!object.has("dependencies")) return result;
        if (!object.get("dependencies").isJsonObject()) throw new IllegalArgumentException("dependencies must be an object");
        for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("dependencies").entrySet()) {
            if (entry.getKey().isBlank() || !entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) throw new IllegalArgumentException("invalid dependency entry");
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static PackMetadata readDirectoryManifest(Path path) throws IOException {
        return readDirectoryManifest(path, false, true);
    }

    private static PackMetadata readDirectoryManifest(Path path, boolean bundled) throws IOException {
        return readDirectoryManifest(path, bundled, true);
    }

    private static PackMetadata readDirectoryManifest(Path path, boolean bundled, boolean validateContents) throws IOException {
        Path manifest = path.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) throw new IOException("missing " + MANIFEST_FILE);
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            PackMetadata metadata = parseManifest(JsonParser.parseReader(reader).getAsJsonObject(), bundled);
            if (validateContents) validateContents(path, metadata);
            return metadata;
        }
    }

    private static PackMetadata readZipManifest(Path path) throws IOException {
        return readZipManifest(path, true);
    }

    private static PackMetadata readZipManifest(Path path, boolean validateContents) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry(MANIFEST_FILE);
            if (entry == null || entry.isDirectory()) throw new IOException("missing " + MANIFEST_FILE);
            try (InputStream stream = zip.getInputStream(entry); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                PackMetadata metadata = parseManifest(JsonParser.parseReader(reader).getAsJsonObject());
                if (validateContents) validateContents(path, metadata);
                return metadata;
            }
        }
    }

    private static boolean dependenciesMatch(Map<String, String> dependencies) throws InvalidVersionSpecificationException {
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            VersionRange range = VersionRange.createFromVersionSpec(entry.getValue());
            ArtifactVersion version = ModList.get().getModContainerById(entry.getKey()).map(mod -> mod.getModInfo().getVersion()).orElse(null);
            if (version == null || !range.containsVersion(version)) { LOGGER.warn("Prismod resource pack dependency {} {} is not satisfied", entry.getKey(), entry.getValue()); return false; }
        }
        return true;
    }

    private static boolean isZip(Path path) { return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"); }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination); else Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static String safeMessage(Exception exception) { String message = exception.getMessage(); return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message; }

    private static final class PrismodPackResources extends AbstractPackResources {
        private final Path source;
        private final String namespace;
        private final Set<String> allowedDirectories;

        private PrismodPackResources(PackCandidate candidate) {
            super(packIdForNamespace(candidate.metadata().namespace()), false);
            source = candidate.path(); namespace = candidate.metadata().namespace();
            Set<String> directories = new HashSet<>(candidate.metadata().filters().stream().map(entry -> entry.path() + "/").toList());
            directories.add(String.format(Locale.ROOT, LANGUAGE_DIRECTORY, namespace));
            if (candidate.bundled()) directories.add("assets/prismod/runtime/");
            allowedDirectories = Set.copyOf(directories);
        }

        @Override public IoSupplier<InputStream> getRootResource(String... paths) { return null; }

        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            if (type != PackType.CLIENT_RESOURCES || !namespace.equals(id.getNamespace())) return null;
            String relative = "assets/" + namespace + "/" + id.getPath();
            if (allowedDirectories.stream().noneMatch(relative::startsWith)) return null;
            return supplier(relative);
        }

        @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
            if (type != PackType.CLIENT_RESOURCES || !this.namespace.equals(namespace)) return;
            for (String directory : allowedDirectories) listDirectory(directory, prefix, output);
        }

        private void listDirectory(String directory, String prefix, ResourceOutput output) {
            String requested = "assets/" + namespace + "/" + prefix;
            if (!directory.startsWith(requested) && !requested.startsWith(directory)) return;
            if (Files.isDirectory(source)) {
                Path root = source.resolve(directory).normalize();
                if (!root.startsWith(source) || !Files.isDirectory(root)) return;
                try (var paths = Files.walk(root)) { paths.filter(Files::isRegularFile).forEach(path -> publish(source.relativize(path).toString().replace('\\', '/'), output)); }
                catch (IOException exception) { LOGGER.warn("Unable to list Prismod resources in {}", source, exception); }
                return;
            }
            try (ZipFile zip = new ZipFile(source.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) { ZipEntry entry = entries.nextElement(); if (!entry.isDirectory() && entry.getName().startsWith(directory)) publish(entry.getName(), output); }
            } catch (IOException exception) { LOGGER.warn("Unable to list Prismod resources in {}", source, exception); }
        }

        private void publish(String relative, ResourceOutput output) {
            String prefix = "assets/" + namespace + "/";
            if (relative.startsWith(prefix)) output.accept(ResourceLocation.fromNamespaceAndPath(namespace, relative.substring(prefix.length())), supplier(relative));
        }

        private IoSupplier<InputStream> supplier(String relative) {
            if (Files.isDirectory(source)) { Path file = source.resolve(relative).normalize(); return file.startsWith(source) && Files.isRegularFile(file) ? IoSupplier.create(file) : null; }
            try (ZipFile zip = new ZipFile(source.toFile())) { ZipEntry entry = zip.getEntry(relative); if (entry == null || entry.isDirectory()) return null; }
            catch (IOException exception) { return null; }
            return () -> {
                ZipFile zip = new ZipFile(source.toFile()); ZipEntry entry = zip.getEntry(relative);
                if (entry == null || entry.isDirectory()) { zip.close(); throw new IOException("Missing ZIP entry " + relative); }
                InputStream stream = zip.getInputStream(entry);
                return new FilterInputStream(stream) { @Override public void close() throws IOException { try { super.close(); } finally { zip.close(); } } };
            };
        }

        @Override public Set<String> getNamespaces(PackType type) { return type == PackType.CLIENT_RESOURCES ? Set.of(namespace) : Set.of(); }
        @Override public void close() { }
    }

    public static final class PrismodResourceManager implements ResourceManager, AutoCloseable {
        private final ResourceManager vanilla;
        private final List<PrismodPackResources> packs;
        private final List<PackCandidate> candidates;
        private final Map<ResourceLocation, ResourceLocation> virtualResources = new HashMap<>();
        /** Prismod 自己的显示翻译表，不注册到 Minecraft LanguageManager。 */
        private final Map<String, Map<String, String>> translations = new HashMap<>();

        PrismodResourceManager(ResourceManager vanilla, List<PackCandidate> candidates) {
            this(vanilla, null, candidates);
        }

        PrismodResourceManager(ResourceManager vanilla, PackCandidate bundled, List<PackCandidate> candidates) {
            this.vanilla = vanilla;
            List<PackCandidate> all = new ArrayList<>();
            if (bundled != null) all.add(bundled);
            all.addAll(candidates);
            this.candidates = all.stream().filter(candidate -> candidate.bundled() || PrismodClientConfig.isPackEnabled(candidate.metadata().namespace())).toList();
            packs = this.candidates.stream().map(PrismodPackResources::new).toList();
            loadTranslations();
        }

        public List<PackCandidate> candidates() { return candidates; }

        private Optional<Resource> getPrivateResource(ResourceLocation id) {
            for (PrismodPackResources pack : packs) {
                if (!pack.namespace.equals(id.getNamespace())) continue;
                IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, id);
                if (supplier != null) return Optional.of(new Resource(pack, supplier));
            }
            return Optional.empty();
        }

        public synchronized Optional<String> translate(String namespace, String key) {
            if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) return Optional.empty();
            Map<String, String> language = translations.get(namespace);
            return language == null ? Optional.empty() : Optional.ofNullable(language.get(key));
        }

        private void loadTranslations() {
            String selected = selectedLanguage();
            for (PrismodPackResources pack : packs) {
                Map<String, String> merged = new HashMap<>(loadLanguage(pack.namespace, "en_us"));
                if (!"en_us".equals(selected)) merged.putAll(loadLanguage(pack.namespace, selected));
                translations.put(pack.namespace, Map.copyOf(merged));
            }
        }

        private Map<String, String> loadLanguage(String namespace, String language) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "lang/" + language + ".json");
            Optional<Resource> resource = getPrivateResource(id);
            if (resource.isEmpty()) return Map.of();
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) throw new IllegalArgumentException("language file must contain an object");
                return parseLanguage(element.getAsJsonObject());
            } catch (Exception exception) {
                LOGGER.warn("Unable to load Prismod language file {}", id, exception);
                return Map.of();
            }
        }

        private static String selectedLanguage() {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null && minecraft.getLanguageManager() != null) {
                    String selected = minecraft.getLanguageManager().getSelected();
                    if (selected != null && !selected.isBlank()) return selected.toLowerCase(Locale.ROOT);
                }
            } catch (RuntimeException ignored) {
                // Unit tests and early bootstrap can run without a Minecraft client.
            }
            return "en_us";
        }

        public synchronized void registerVirtualResources(Map<ResourceLocation, ResourceLocation> mappings) {
            mappings.forEach((virtual, physical) -> { if (!virtual.getNamespace().equals(physical.getNamespace())) throw new IllegalArgumentException("virtual resource namespace mismatch"); virtualResources.put(virtual, physical); });
        }

        @Override public Set<String> getNamespaces() { Set<String> namespaces = new HashSet<>(vanilla.getNamespaces()); packs.forEach(pack -> namespaces.addAll(pack.getNamespaces(PackType.CLIENT_RESOURCES))); return Set.copyOf(namespaces); }

        @Override public java.util.Optional<Resource> getResource(ResourceLocation id) {
            ResourceLocation resolved; synchronized (this) { resolved = virtualResources.getOrDefault(id, id); }
            for (PrismodPackResources pack : packs) { IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, resolved); if (supplier != null) return java.util.Optional.of(new Resource(pack, supplier)); }
            return vanilla.getResource(id);
        }

        @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).map(List::of).orElseGet(() -> vanilla.getResourceStack(id)); }

        @Override public Map<ResourceLocation, Resource> listResources(String prefix, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, Resource> result = new HashMap<>(vanilla.listResources(prefix, filter));
            for (PrismodPackResources pack : packs) pack.listResources(PackType.CLIENT_RESOURCES, pack.namespace, prefix, (id, supplier) -> { if (filter.test(id)) result.put(id, new Resource(pack, supplier)); });
            return Map.copyOf(result);
        }

        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String prefix, Predicate<ResourceLocation> filter) { return listResources(prefix, filter).entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.of(entry.getValue()))); }
        @Override public Stream<PackResources> listPacks() { return Stream.concat(vanilla.listPacks(), packs.stream().map(pack -> pack)); }
        @Override public void close() { packs.forEach(PrismodPackResources::close); synchronized (this) { virtualResources.clear(); translations.clear(); } }
    }

    static Map<String, String> parseLanguage(JsonObject object) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().isBlank() || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()) continue;
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(result);
    }

    public record PackCandidate(Path path, PackMetadata metadata, boolean bundled) {
        public PackCandidate(Path path, PackMetadata metadata) { this(path, metadata, false); }
        public String fileName() { return path == null ? "Prismod 内置资源" : path.getFileName().toString(); }
    }
    public record PackMetadata(String namespace, String name, Map<String, String> dependencies, List<PackFilterEntry> filters) {
        public PackMetadata(String namespace, String name, Map<String, String> dependencies) {
            this(namespace, name, dependencies, List.of());
        }
        public String displayName(String fallback) { return name == null || name.isBlank() ? fallback : name; }
    }
    public record PackFilterEntry(String id, String path) {
        public String relativeFilterDirectory(String namespace) {
            return path.substring((ASSETS_DIRECTORY + "/" + namespace + "/filters/").length());
        }
    }
    public record ImportResult(boolean success, String message, PackCandidate candidate) { static ImportResult success(PackCandidate candidate) { return new ImportResult(true, "资源包已导入。", candidate); } static ImportResult failure(String message) { return new ImportResult(false, message, null); } }
}
