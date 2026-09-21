package com.xkmxz.prismod.client.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xkmxz.prismod.client.filter.registry.FilterManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 资源包编辑器的文件工作区、校验和原子保存实现。 */
public final class ResourcePackEditorService {
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".json", ".fsh", ".vsh", ".glsl", ".cube", ".txt", ".md");
    private static final String RECYCLE_DIRECTORY = ".prismod-recycle";

    private ResourcePackEditorService() { }

    public static Session open(PrismodPackLoader.PackCandidate candidate) throws IOException {
        if (candidate == null || candidate.path() == null) throw new IOException("资源包不存在");
        Path source = candidate.path();
        Path working = source;
        if (Files.isRegularFile(source)) {
            String fileName = source.getFileName().toString();
            String base = fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                    ? fileName.substring(0, fileName.length() - 4) : fileName;
            working = source.resolveSibling(base + ".editable");
            if (Files.exists(working)) throw new IOException("可编辑目录已存在：" + working.getFileName());
            unzip(source, working);
        }
        if (!Files.isDirectory(working)) throw new IOException("资源包目录不可用");
        Map<String, String> files = readEditableFiles(working);
        return new Session(source, working, candidate.metadata(), files);
    }

    public static List<String> editableFiles(Path root, String namespace) throws IOException {
        return readEditableFiles(root).keySet().stream()
                .filter(path -> isSupported(path, namespace))
                .sorted().toList();
    }

    public static SaveResult save(Session session, String name, String namespace,
                                  Map<String, String> editedFiles) {
        if (session == null) return SaveResult.failure("编辑会话不存在");
        try {
            if (!net.minecraft.resources.ResourceLocation.isValidNamespace(namespace)
                    || Set.of("minecraft", "prismod").contains(namespace)) {
                return SaveResult.failure("namespace 无效或为保留名称");
            }
            Path sourceRoot = session.workingDirectory();
            Map<String, String> files = new LinkedHashMap<>(session.files());
            if (editedFiles != null) files.putAll(editedFiles);
            String oldNamespace = session.metadata().namespace();
            if (!oldNamespace.equals(namespace)) files = rewriteNamespace(files, oldNamespace, namespace);
            JsonObject manifest = parseObject(files.get(PrismodPackLoader.MANIFEST_FILE), PrismodPackLoader.MANIFEST_FILE);
            manifest.addProperty("namespace", namespace);
            if (name == null || name.isBlank()) manifest.remove("name");
            else manifest.addProperty("name", name.trim());
            files.put(PrismodPackLoader.MANIFEST_FILE,
                    new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
            Validation validation = validate(files, namespace);
            if (!validation.valid()) return SaveResult.failure(validation.message());

            Path base = sourceRoot.getParent();
            String sourceName = sourceRoot.getFileName().toString();
            Path target = oldNamespace.equals(namespace) ? sourceRoot : base.resolve(sourceName + "-" + namespace);
            if (!target.equals(sourceRoot) && Files.exists(target)) return SaveResult.failure("目标资源包目录已存在");
            Path temp = base.resolve("." + sourceName + ".prismod-tmp-" + UUID.randomUUID());
            writeTree(temp, files);
            Path backup = sourceRoot.resolveSibling(sourceName + ".prismod-backup");
            if (target.equals(sourceRoot)) {
                if (Files.exists(backup)) deleteTree(backup);
                Files.move(sourceRoot, backup, StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (Exception failure) { Files.move(backup, sourceRoot, StandardCopyOption.REPLACE_EXISTING); deleteTree(temp); throw failure; }
            } else {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            }
            return SaveResult.success(target, validation.warnings());
        } catch (Exception exception) {
            return SaveResult.failure(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    public static FilterCreation createFilter(Session session, String id, String type) throws IOException {
        if (session == null || !net.minecraft.resources.ResourceLocation.isValidPath(id)) throw new IOException("滤镜 ID 无效");
        String namespace = session.metadata().namespace();
        String root = "assets/" + namespace + "/filters/" + id;
        Map<String, String> additions = new LinkedHashMap<>();
        boolean lut = "lut3d".equalsIgnoreCase(type);
        additions.put(root + "/filter.json", "{\n  \"schema\": \"prismod.filter\",\n  \"format_version\": 1,\n  \"type\": \"" + (lut ? "lut3d" : "post_chain") + "\",\n  \"display_name\": \"filter." + namespace + "." + id.replace('/', '.') + "\",\n  \"source\": \"" + (lut ? "lut.cube" : "post.json") + "\",\n  \"color_space\": \"sRGB\",\n  \"default_strength\": 1.0\n}\n");
        additions.put(root + (lut ? "/lut.cube" : "/post.json"), lut ? "LUT_3D_SIZE 2\n0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1\n" : "{\n  \"targets\": [\"swap\"],\n  \"passes\": []\n}\n");
        return new FilterCreation(id, additions);
    }

    private static Validation validate(Map<String, String> files, String namespace) {
        try {
            JsonObject manifest = parseObject(files.get(PrismodPackLoader.MANIFEST_FILE), PrismodPackLoader.MANIFEST_FILE);
            PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(manifest);
            if (!namespace.equals(metadata.namespace())) return Validation.error("清单 namespace 与编辑目标不一致");
            for (PrismodPackLoader.PackFilterEntry entry : metadata.filters()) {
                String filterPath = entry.path() + "/filter.json";
                JsonObject filter = parseObject(files.get(filterPath), filterPath);
                FilterManifest.parse(filter);
            }
            return Validation.ok();
        } catch (Exception exception) {
            return Validation.error("校验失败：" + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private static JsonObject parseObject(String text, String path) {
        if (text == null) throw new IllegalArgumentException("缺少文件：" + path);
        JsonElement element = JsonParser.parseString(text);
        if (!element.isJsonObject()) throw new IllegalArgumentException(path + " 必须是 JSON 对象");
        return element.getAsJsonObject();
    }

    private static Map<String, String> rewriteNamespace(Map<String, String> files, String oldNamespace, String namespace) {
        Map<String, String> result = new LinkedHashMap<>();
        String oldPrefix = "assets/" + oldNamespace + "/";
        String newPrefix = "assets/" + namespace + "/";
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey().startsWith(oldPrefix) ? newPrefix + entry.getKey().substring(oldPrefix.length()) : entry.getKey();
            String content = entry.getValue().replace("assets/" + oldNamespace + "/", newPrefix)
                    .replace("filter." + oldNamespace + ".", "filter." + namespace + ".");
            result.put(path, content);
        }
        return result;
    }

    private static Map<String, String> readEditableFiles(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (isSupported(relative, null)) files.put(relative, Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static boolean isSupported(String path, String namespace) {
        if (path.equals(PrismodPackLoader.MANIFEST_FILE)) return true;
        if (namespace != null && !path.startsWith("assets/" + namespace + "/")) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith) && !path.contains("..") && !path.startsWith("/");
    }

    private static void unzip(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path destination = target.resolve(entry.getName()).normalize();
                if (!destination.startsWith(target)) throw new IOException("ZIP 含有越界路径");
                if (entry.isDirectory()) Files.createDirectories(destination);
                else { Files.createDirectories(destination.getParent()); try (var input = archive.getInputStream(entry)) { Files.copy(input, destination); } }
            }
        }
    }

    private static void writeTree(Path root, Map<String, String> files) throws IOException {
        Files.createDirectories(root);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path destination = root.resolve(entry.getKey()).normalize();
            if (!destination.startsWith(root)) throw new IOException("文件路径越界");
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }

    public record Session(Path originalPath, Path workingDirectory, PrismodPackLoader.PackMetadata metadata,
                          Map<String, String> files) {
        public Session { files = Map.copyOf(files); }
        public List<String> fileNames() { return files.keySet().stream().sorted().toList(); }
    }
    public record FilterCreation(String id, Map<String, String> files) { public FilterCreation { files = Map.copyOf(files); } }
    public record SaveResult(boolean success, String message, Path path, List<String> warnings) {
        public static SaveResult success(Path path, List<String> warnings) { return new SaveResult(true, "资源包已保存", path, List.copyOf(warnings)); }
        public static SaveResult failure(String message) { return new SaveResult(false, message, null, List.of()); }
    }
    private record Validation(boolean valid, String message, List<String> warnings) {
        static Validation ok() { return new Validation(true, "", List.of()); }
        static Validation error(String message) { return new Validation(false, message, List.of()); }
    }
}
