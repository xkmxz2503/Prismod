package com.xkmxz.prismod.client.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xkmxz.prismod.client.filter.registry.FilterManifest;
import com.xkmxz.prismod.client.filter.lut.LutCubeParser;

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
    public static final String BACKUP_DIRECTORY = ".prismod-backup";
    public static final String BACKUP_ROOT_DIRECTORY = "backup/resourcepacks";
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".json", ".fsh", ".vsh", ".glsl", ".cube", ".txt", ".md");
    private static final String RECYCLE_DIRECTORY = ".prismod-recycle";
    private static final List<String> DEBUG_UNIFORMS = List.of(
            "Intensity", "Exposure", "Contrast", "Highlights", "Shadows",
            "Saturation", "Temperature", "Tint", "Gamma");

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
        return save(session, name, namespace, editedFiles, List.of());
    }

    public static SaveResult save(Session session, String name, String namespace,
                                  Map<String, String> editedFiles,
                                  List<Map<String, String>> deletedBatches) {
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
            Validation validation = validatePackFiles(files, namespace);
            if (!validation.valid()) return SaveResult.failure(validation.message());

            Path base = sourceRoot.getParent().toAbsolutePath().normalize();
            String sourceName = sourceRoot.getFileName().toString();
            Path target = oldNamespace.equals(namespace) ? sourceRoot : base.resolve(sourceName + "-" + namespace);
            if (!target.equals(sourceRoot) && Files.exists(target)) return SaveResult.failure("目标资源包目录已存在");
            Path temp = base.resolve("." + sourceName + ".prismod-tmp-" + UUID.randomUUID());
            writeTree(temp, files);
            // 先写入与资源包本体隔离的删除备份，避免保存完成后才发现备份失败。
            writeRecycleBatches(base, recyclePackName(session), deletedBatches);
            if (target.equals(sourceRoot)) {
                Path backupRoot = backupRootFor(sourceRoot.getParent()).resolve(BACKUP_DIRECTORY);
                Path backup = backupRoot.resolve(sourceName);
                Files.createDirectories(backupRoot);
                if (Files.exists(backup)) deleteTree(backup);
                moveWithoutReplace(sourceRoot, backup);
                try { moveWithoutReplace(temp, target); }
                catch (Exception failure) { Files.move(backup, sourceRoot, StandardCopyOption.REPLACE_EXISTING); deleteTree(temp); throw failure; }
            } else {
                moveWithoutReplace(temp, target);
            }
            return SaveResult.success(target, validation.warnings());
        } catch (Exception exception) {
            return SaveResult.failure(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private static String recyclePackName(Session session) throws IOException {
        Path original = session.originalPath();
        String name = original.getFileName().toString();
        if (Files.isRegularFile(original) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.endsWith(".editable")) name = name.substring(0, name.length() - ".editable".length());
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
            throw new IOException("删除备份资源包名称无效");
        }
        return name;
    }

    private static void writeRecycleBatches(Path resourcePacksDirectory, String sourceName,
                                            List<Map<String, String>> deletedBatches) throws IOException {
        if (deletedBatches == null || deletedBatches.isEmpty()) return;
        Path root = resourcePacksDirectory.toAbsolutePath().normalize();
        Path recycleBase = backupRootFor(root).resolve(RECYCLE_DIRECTORY).normalize();
        Path recycleRoot = recycleBase.resolve(sourceName).normalize();
        if (!recycleRoot.startsWith(recycleBase) || sourceName.contains("..")) {
            throw new IOException("删除备份路径越界");
        }
        Files.createDirectories(recycleRoot);
        for (Map<String, String> batch : deletedBatches) {
            if (batch == null || batch.isEmpty()) continue;
            Path batchDirectory;
            long timestamp = System.currentTimeMillis();
            do { batchDirectory = recycleRoot.resolve(Long.toString(timestamp++)).normalize(); }
            while (Files.exists(batchDirectory));
            if (!batchDirectory.startsWith(recycleRoot)) throw new IOException("删除备份批次路径越界");
            writeTree(batchDirectory, batch);
        }
    }

    /** 清除统一备份目录及历史版本留下的同级备份目录。 */
    public static int clearBackups() throws IOException {
        return clearBackups(PrismodPackLoader.resourcePacksDirectory());
    }

    static int clearBackups(Path resourcePacksDirectory) throws IOException {
        if (resourcePacksDirectory == null || !Files.isDirectory(resourcePacksDirectory)) return 0;
        int removed = 0;
        Path backupRoot = backupRootFor(resourcePacksDirectory);
        removed += clearBackupDirectory(backupRoot.resolve(BACKUP_DIRECTORY));
        // 清理旧版本的完整备份目录；删除回收站从未公开写入资源包内部。
        removed += clearBackupDirectory(resourcePacksDirectory.resolve(BACKUP_DIRECTORY));
        try (var paths = Files.list(resourcePacksDirectory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!name.equals(BACKUP_DIRECTORY) && name.endsWith(".prismod-backup")) {
                    deleteTree(path);
                    removed++;
                }
            }
        }
        return removed;
    }

    public static BackupSnapshot inspectBackups() throws IOException {
        return inspectBackups(PrismodPackLoader.resourcePacksDirectory());
    }

    static BackupSnapshot inspectBackups(Path resourcePacksDirectory) throws IOException {
        Path root = checkedResourcePacksRoot(resourcePacksDirectory);
        Path backupRoot = backupRootFor(root);
        List<BackupEntry> versions = listEntries(backupRoot.resolve(BACKUP_DIRECTORY), false);
        List<BackupEntry> recycled = listEntries(backupRoot.resolve(RECYCLE_DIRECTORY), true);
        return new BackupSnapshot(versions, recycled, List.of());
    }

    public static CleanupResult clearVersionBackups(String packName) throws IOException {
        return clearVersionBackups(PrismodPackLoader.resourcePacksDirectory(), packName);
    }

    static CleanupResult clearVersionBackups(Path root, String packName) throws IOException {
        return clearEntries(backupRootFor(checkedResourcePacksRoot(root)).resolve(BACKUP_DIRECTORY), packName, null, true);
    }

    public static CleanupResult clearRecycleBackups(String packName, String batchName) throws IOException {
        return clearRecycleBackups(PrismodPackLoader.resourcePacksDirectory(), packName, batchName);
    }

    static CleanupResult clearRecycleBackups(Path root, String packName, String batchName) throws IOException {
        Path resourceRoot = checkedResourcePacksRoot(root);
        return clearEntries(backupRootFor(resourceRoot).resolve(RECYCLE_DIRECTORY), packName, batchName, false);
    }

    private static CleanupResult clearEntries(Path parent, String packName, String batchName, boolean versions) throws IOException {
        if (packName != null && !isSafePathName(packName)) throw new IOException("资源包备份名称无效");
        if (batchName != null && !isSafePathName(batchName)) throw new IOException("删除备份批次名称无效");
        if (!Files.isDirectory(parent)) return new CleanupResult(0, 0, 0);
        int count = 0;
        try (var packs = Files.list(parent)) {
            for (Path pack : packs.toList()) {
                if (!Files.isDirectory(pack) || (packName != null && !packName.equals(pack.getFileName().toString()))) continue;
                if (versions || batchName == null) {
                    deleteTree(pack);
                    count++;
                } else {
                    Path batch = pack.resolve(batchName).normalize();
                    if (batch.startsWith(pack) && Files.isDirectory(batch)) {
                        deleteTree(batch);
                        count++;
                    }
                }
            }
        }
        return versions ? new CleanupResult(count, 0, 0) : new CleanupResult(0, count, 0);
    }

    private static List<BackupEntry> listEntries(Path parent, boolean batches) throws IOException {
        if (!Files.isDirectory(parent)) return List.of();
        List<BackupEntry> result = new ArrayList<>();
        try (var packs = Files.list(parent)) {
            for (Path pack : packs.filter(Files::isDirectory).toList()) {
                if (isSpecialDirectory(pack)) continue;
                if (!batches) {
                    result.add(entry(pack, pack.getFileName().toString(), null));
                    continue;
                }
                try (var batchPaths = Files.list(pack)) {
                    for (Path batch : batchPaths.filter(Files::isDirectory).toList()) {
                        result.add(entry(batch, pack.getFileName().toString(), batch.getFileName().toString()));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static BackupEntry entry(Path path, String packName, String batchName) throws IOException {
        long bytes = 0;
        int files = 0;
        try (var paths = Files.walk(path)) {
            for (Path child : paths.filter(Files::isRegularFile).toList()) {
                files++;
                bytes += Files.size(child);
            }
        }
        long timestamp = batchName == null ? Files.getLastModifiedTime(path).toMillis() : parseTimestamp(batchName, Files.getLastModifiedTime(path).toMillis());
        return new BackupEntry(packName, batchName, timestamp, files, bytes, path);
    }

    private static long parseTimestamp(String value, long fallback) {
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static Path checkedResourcePacksRoot(Path root) throws IOException {
        if (root == null) throw new IOException("资源包目录不可用");
        Path normalized = root.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        return normalized;
    }

    private static Path backupRootFor(Path resourcePacksDirectory) throws IOException {
        Path root = resourcePacksDirectory.toAbsolutePath().normalize();
        Path parent = root.getParent();
        if (parent == null) throw new IOException("资源包目录不可用");
        Path backupRoot = parent.resolve(BACKUP_ROOT_DIRECTORY).normalize();
        Path expectedParent = parent.toAbsolutePath().normalize();
        if (!backupRoot.startsWith(expectedParent) || backupRoot.equals(root)) {
            throw new IOException("备份目录路径无效");
        }
        Files.createDirectories(backupRoot);
        return backupRoot;
    }

    private static int clearBackupDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return 0;
        int count = 1;
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                count = (int) entries.count();
            }
        }
        deleteTree(path);
        return count;
    }

    private static boolean isSpecialDirectory(Path path) {
        String name = path.getFileName().toString();
        return name.equals(BACKUP_DIRECTORY) || name.equals(RECYCLE_DIRECTORY) || name.endsWith(".prismod-backup");
    }

    private static boolean isSafePathName(String value) {
        return value != null && !value.isBlank() && !value.equals(".") && !value.equals("..")
                && !value.contains("/") && !value.contains("\\") && !value.contains("..") ;
    }

    public static CreatePackResult createPack(CreatePackRequest request) {
        PrismodPackLoader.ensureDirectories();
        return createPack(PrismodPackLoader.resourcePacksDirectory(), request);
    }

    static CreatePackResult createPack(Path resourcePacksDirectory, CreatePackRequest request) {
        if (request == null) return CreatePackResult.failure("创建参数不能为空");
        String namespace = request.namespace() == null ? "" : request.namespace().trim();
        if (!net.minecraft.resources.ResourceLocation.isValidNamespace(namespace)
                || Set.of("minecraft", "prismod").contains(namespace)) {
            return CreatePackResult.failure("namespace 无效或为保留名称");
        }
        if (resourcePacksDirectory == null) return CreatePackResult.failure("资源包目录不可用");
        Path target = resourcePacksDirectory.resolve(namespace).normalize();
        Path temp = resourcePacksDirectory.resolve("." + namespace + ".prismod-create-" + UUID.randomUUID());
        try {
            Files.createDirectories(resourcePacksDirectory);
            if (Files.exists(target)) return CreatePackResult.failure("目标资源包目录已存在");
            for (PrismodPackLoader.PackCandidate candidate : PrismodPackLoader.scan(resourcePacksDirectory)) {
                if (namespace.equals(candidate.metadata().namespace())) {
                    return CreatePackResult.failure("资源包 namespace 已存在");
                }
            }
            Map<String, String> files = new LinkedHashMap<>();
            files.put(PrismodPackLoader.MANIFEST_FILE, emptyPackManifest(
                    namespace, request.name()));
            if (request.includeReadme()) files.put("README.md", creationReadme(namespace));
            Validation validation = validatePackFiles(files, namespace);
            if (!validation.valid()) return CreatePackResult.failure(validation.message());
            writeTree(temp, files);
            moveWithoutReplace(temp, target);
            return CreatePackResult.success(target);
        } catch (Exception exception) {
            return CreatePackResult.failure(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
        } finally {
            if (Files.exists(temp)) {
                try { deleteTree(temp); }
                catch (IOException ignored) { }
            }
        }
    }

    public static FilterCreation createFilter(Session session, String id, String type) throws IOException {
        return createFilter(session, new FilterCreateRequest(id, type, 1.0F, Map.of(), Map.of()));
    }

    /** Creates a complete runnable filter and overlays imported files on the generated template. */
    public static FilterCreation createFilter(Session session, FilterCreateRequest request) throws IOException {
        if (session == null) throw new IOException("编辑会话不存在");
        if (request == null || !net.minecraft.resources.ResourceLocation.isValidPath(request.id())) {
            throw new IOException("滤镜 ID 无效");
        }
        String type = "lut3d".equalsIgnoreCase(request.type()) ? "lut3d" : "post_chain";
        if (!Float.isFinite(request.defaultStrength()) || request.defaultStrength() < 0.0F || request.defaultStrength() > 1.0F) {
            throw new IOException("默认强度必须在 0 到 1 之间");
        }
        String namespace = session.metadata().namespace();
        String root = "assets/" + namespace + "/filters/" + request.id();
        String key = "filter." + namespace + "." + request.id().replace('/', '.');
        Map<String, String> additions = new LinkedHashMap<>();
        boolean lut = "lut3d".equals(type);
        additions.put(root + "/filter.json", filterJson(type, key, lut ? "lut.cube" : "post.json", request.defaultStrength()));
        if (lut) {
            additions.put(root + "/lut.cube", identityCube(32));
        } else {
            for (Map.Entry<String, String> entry : postChainTemplate(namespace, request.id()).entrySet()) {
                additions.put(root + "/" + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, String> imported : request.importedFiles().entrySet()) {
            String relative = normalizeImportedPath(imported.getKey());
            additions.put(root + "/" + relative, imported.getValue());
        }
        String filterText = additions.get(root + "/filter.json");
        JsonObject filter = parseObject(filterText, root + "/filter.json");
        filter.addProperty("display_name", key);
        filter.addProperty("default_strength", request.defaultStrength());
        additions.put(root + "/filter.json", new GsonBuilder().setPrettyPrinting().create().toJson(filter));
        validateNewFilter(additions, root);
        return new FilterCreation(request.id(), type, key, additions, request.names());
    }

    /** Reads a dropped file or directory into filter-relative paths. */
    public static Map<String, String> readImportedFiles(List<Path> dropped) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (dropped == null) return result;
        for (Path input : dropped) {
            if (input == null) continue;
            Path source = input.toAbsolutePath().normalize();
            if (Files.isDirectory(source)) {
                Path base = source;
                try (var files = Files.walk(source)) {
                    List<Path> regular = files.filter(Files::isRegularFile).toList();
                    Path manifest = regular.stream().filter(path -> path.getFileName().toString().equalsIgnoreCase("filter.json")).findFirst().orElse(null);
                    if (manifest != null) base = manifest.getParent();
                    for (Path file : regular) {
                        if (!isImportExtension(file.getFileName().toString())) continue;
                        String relative = base.relativize(file).toString().replace('\\', '/');
                        result.put(normalizeImportedPath(relative), Files.readString(file, StandardCharsets.UTF_8));
                    }
                }
            } else if (Files.isRegularFile(source)) {
                if (!isImportExtension(source.getFileName().toString())) throw new IOException("不支持导入文件：" + source.getFileName());
                result.put(normalizeSingleFilePath(source.getFileName().toString()), Files.readString(source, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static String filterJson(String type, String key, String source, float strength) {
        JsonObject filter = new JsonObject();
        filter.addProperty("schema", "prismod.filter");
        filter.addProperty("format_version", 1);
        filter.addProperty("type", type);
        filter.addProperty("display_name", key);
        filter.addProperty("source", source);
        if ("lut3d".equals(type)) filter.addProperty("color_space", "sRGB");
        filter.addProperty("default_strength", strength);
        return new GsonBuilder().setPrettyPrinting().create().toJson(filter) + "\n";
    }

    private static Map<String, String> postChainTemplate(String namespace, String id) {
        String fragment = namespace + ":" + id;
        JsonObject post = new JsonObject();
        JsonArray targets = new JsonArray();
        targets.add("swap");
        post.add("targets", targets);
        JsonArray passes = new JsonArray();
        JsonObject pass = new JsonObject();
        pass.addProperty("name", fragment);
        pass.addProperty("intarget", "minecraft:main");
        pass.addProperty("outtarget", "swap");
        JsonArray uniforms = new JsonArray();
        for (String uniform : DEBUG_UNIFORMS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", uniform);
            entry.addProperty("type", "float");
            JsonArray values = new JsonArray();
            values.add("Intensity".equals(uniform) || "Saturation".equals(uniform) || "Gamma".equals(uniform) ? 1.0 : 0.0);
            entry.add("values", values);
            uniforms.add(entry);
        }
        pass.add("uniforms", uniforms);
        passes.add(pass);
        post.add("passes", passes);
        JsonObject programJson = new JsonObject();
        programJson.addProperty("vertex", namespace + ":fullscreen");
        programJson.addProperty("fragment", fragment);
        JsonArray attributes = new JsonArray(); attributes.add("Position"); programJson.add("attributes", attributes);
        JsonArray samplers = new JsonArray(); JsonObject sampler = new JsonObject(); sampler.addProperty("name", "DiffuseSampler"); samplers.add(sampler); programJson.add("samplers", samplers);
        JsonArray programUniforms = new JsonArray();
        JsonObject projection = new JsonObject();
        projection.addProperty("name", "ProjMat"); projection.addProperty("type", "matrix4x4"); projection.addProperty("count", 16);
        JsonArray projectionValues = new JsonArray();
        for (int index = 0; index < 16; index++) projectionValues.add(index % 5 == 0 ? 1.0 : 0.0);
        projection.add("values", projectionValues); programUniforms.add(projection);
        for (String sizeUniform : List.of("OutSize", "ScreenSize")) {
            JsonObject entry = new JsonObject(); entry.addProperty("name", sizeUniform); entry.addProperty("type", "float"); entry.addProperty("count", 2);
            JsonArray values = new JsonArray(); values.add(1.0); values.add(1.0); entry.add("values", values); programUniforms.add(entry);
        }
        for (String uniform : DEBUG_UNIFORMS) {
            JsonObject entry = new JsonObject(); entry.addProperty("name", uniform); entry.addProperty("type", "float"); entry.addProperty("count", 1);
            JsonArray values = new JsonArray(); values.add("Intensity".equals(uniform) || "Saturation".equals(uniform) || "Gamma".equals(uniform) ? 1.0 : 0.0); entry.add("values", values); programUniforms.add(entry);
        }
        programJson.add("uniforms", programUniforms);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("program/" + id + ".json", new GsonBuilder().setPrettyPrinting().create().toJson(programJson) + "\n");
        result.put("post.json", new GsonBuilder().setPrettyPrinting().create().toJson(post) + "\n");
        result.put("program/fullscreen.vsh", fullscreenVertexShader());
        result.put("program/" + id + ".fsh", genericFragmentShader());
        return result;
    }

    private static void validateNewFilter(Map<String, String> files, String root) throws IOException {
        JsonObject filter = parseObject(files.get(root + "/filter.json"), root + "/filter.json");
        FilterManifest manifest;
        try { manifest = FilterManifest.parse(filter); }
        catch (RuntimeException exception) { throw new IOException("校验失败：" + exception.getMessage(), exception); }
        String sourcePath = root + "/" + manifest.source();
        if (!files.containsKey(sourcePath)) throw new IOException("校验失败：缺少源文件 " + sourcePath);
        if (manifest.type() == com.xkmxz.prismod.client.filter.registry.FilterType.LUT3D) {
            validateCube(files.get(sourcePath), sourcePath);
            return;
        }
        JsonObject post = parseObject(files.get(sourcePath), sourcePath);
        if (!post.has("passes") || !post.get("passes").isJsonArray() || post.getAsJsonArray("passes").isEmpty()) {
            throw new IOException("校验失败：" + sourcePath + " 缺少可运行 pass");
        }
        JsonObject pass = post.getAsJsonArray("passes").get(0).getAsJsonObject();
        String programName = pass.has("name") ? pass.get("name").getAsString() : "";
        int separator = programName.indexOf(':');
        String programPath = separator >= 0 ? programName.substring(separator + 1) : programName;
        String programFile = root + "/program/" + programPath + ".json";
        JsonObject program = parseObject(files.get(programFile), programFile);
        for (String shaderField : List.of("vertex", "fragment")) {
            if (!program.has(shaderField)) throw new IOException("校验失败：" + programFile + " 缺少 " + shaderField);
            String shaderName = program.get(shaderField).getAsString();
            int shaderSeparator = shaderName.indexOf(':');
            String shaderPath = shaderSeparator >= 0 ? shaderName.substring(shaderSeparator + 1) : shaderName;
            String extension = "vertex".equals(shaderField) ? ".vsh" : ".fsh";
            String shaderFile = root + "/program/" + shaderPath + extension;
            if (!files.containsKey(shaderFile)) throw new IOException("校验失败：缺少 shader " + shaderFile);
        }
        String all = files.values().stream().reduce("", String::concat);
        for (String uniform : DEBUG_UNIFORMS) if (!all.contains(uniform)) throw new IOException("校验失败：缺少 uniform " + uniform);
    }

    private static void validateCube(String text, String path) throws IOException {
        if (text == null) throw new IOException("校验失败：" + path + " 缺少 LUT_3D_SIZE");
        try {
            LutCubeParser.parse(new java.io.StringReader(text));
        } catch (IOException | RuntimeException exception) {
            throw new IOException("校验失败：" + path + " " + exception.getMessage(), exception);
        }
    }

    private static String identityCube(int size) {
        StringBuilder builder = new StringBuilder("LUT_3D_SIZE ").append(size).append('\n');
        for (int r = 0; r < size; r++) for (int g = 0; g < size; g++) for (int b = 0; b < size; b++) {
            builder.append(r / (float) (size - 1)).append(' ').append(g / (float) (size - 1)).append(' ').append(b / (float) (size - 1)).append('\n');
        }
        return builder.toString();
    }

    private static String normalizeImportedPath(String path) throws IOException {
        String normalized = path == null ? "" : path.replace('\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..") || normalized.contains("//")) throw new IOException("导入文件路径不安全：" + path);
        if (normalized.startsWith("assets/")) {
            int filters = normalized.indexOf("/filters/");
            if (filters >= 0) normalized = normalized.substring(filters + "/filters/".length());
        }
        if (normalized.startsWith("filter/")) normalized = normalized.substring("filter/".length());
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".glsl")) {
            normalized = normalized.substring(0, normalized.length() - 5) + ".fsh";
        }
        return normalized;
    }

    private static String normalizeSingleFilePath(String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        if (name.isBlank() || name.contains("/") || name.contains("..")) throw new IOException("导入文件路径不安全：" + fileName);
        String lower = name.toLowerCase(Locale.ROOT);
        if ("filter.json".equals(lower) || "post.json".equals(lower)) return lower;
        if (lower.endsWith(".cube")) return "lut.cube";
        if (lower.endsWith(".fsh") || lower.endsWith(".vsh")) return "program/" + name;
        if (lower.endsWith(".glsl")) return "program/" + name.substring(0, name.length() - 5) + ".fsh";
        if (lower.endsWith(".json")) return "program/" + name;
        throw new IOException("不支持导入文件：" + fileName);
    }

    private static boolean isImportExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String fullscreenVertexShader() {
        return "#version 150\nin vec4 Position;\nuniform mat4 ProjMat;\nuniform vec2 OutSize;\nout vec2 texCoord;\nvoid main() { gl_Position = ProjMat * vec4(Position.xy, 0.0, 1.0); gl_Position.z = 0.2; texCoord = Position.xy / OutSize; }\n";
    }

    private static String genericFragmentShader() {
        return "#version 150\nuniform sampler2D DiffuseSampler;\nuniform float Intensity;\nuniform float Exposure, Contrast, Highlights, Shadows, Saturation, Temperature, Tint, Gamma;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() { vec4 source = texture(DiffuseSampler, texCoord); vec3 color = source.rgb * exp2(Exposure); color = (color - 0.5) * (1.0 + Contrast) + 0.5; color += vec3(Temperature * 0.1 + Tint * 0.05, 0.0, -Temperature * 0.1 + Tint * 0.05); float luma = dot(color, vec3(0.2126, 0.7152, 0.0722)); color = mix(vec3(luma), color, Saturation); color = pow(max(color, vec3(0.0)), vec3(1.0 / max(Gamma, 0.1))); fragColor = vec4(mix(source.rgb, color, clamp(Intensity, 0.0, 1.0)), source.a); }\n";
    }

    private static Validation validatePackFiles(Map<String, String> files, String namespace) {
        try {
            JsonObject manifest = parseObject(files.get(PrismodPackLoader.MANIFEST_FILE), PrismodPackLoader.MANIFEST_FILE);
            PrismodPackLoader.PackMetadata metadata = PrismodPackLoader.parseManifest(manifest);
            if (!namespace.equals(metadata.namespace())) return Validation.error("清单 namespace 与编辑目标不一致");
            for (PrismodPackLoader.PackFilterEntry entry : metadata.filters()) {
                String filterPath = entry.path() + "/filter.json";
                JsonObject filter = parseObject(files.get(filterPath), filterPath);
                FilterManifest parsed = FilterManifest.parse(filter);
                String sourcePath = entry.path() + "/" + parsed.source();
                String source = files.get(sourcePath);
                if (source == null) throw new IOException("缺少源文件：" + sourcePath);
                if (parsed.type() == com.xkmxz.prismod.client.filter.registry.FilterType.LUT3D) {
                    validateCube(source, sourcePath);
                }
            }
            return Validation.ok();
        } catch (Exception exception) {
            return Validation.error("校验失败：" + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private static String emptyPackManifest(String namespace, String name) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", "prismod.resource_pack");
        manifest.addProperty("format_version", 1);
        manifest.addProperty("namespace", namespace);
        if (name != null && !name.isBlank()) manifest.addProperty("name", name.trim());
        manifest.add("filters", new JsonArray());
        return new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n";
    }

    private static String creationReadme(String namespace) {
        return "# Prismod 资源包\n\n"
                + "这是一个 Prismod v1 自定义资源包，namespace 为 `" + namespace + "`。\n\n"
                + "当前资源包尚未添加滤镜。请在 Prismod 资源包编辑器中使用“新建滤镜”或导入已有滤镜，"
                + "然后保存资源包。\n\n"
                + "本资源包使用 Prismod 私有资源包格式，不需要 `pack.mcmeta`。\n";
    }

    private static void moveWithoutReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
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
        if (path.equals(RECYCLE_DIRECTORY) || path.startsWith(RECYCLE_DIRECTORY + "/")
                || path.equals(BACKUP_DIRECTORY)
                || path.startsWith(BACKUP_DIRECTORY + "/")) return false;
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
    public record CreatePackRequest(String name, String namespace, boolean includeReadme) { }
    public record CreatePackResult(boolean success, String message, Path path) {
        public static CreatePackResult success(Path path) { return new CreatePackResult(true, "资源包已创建", path); }
        public static CreatePackResult failure(String message) { return new CreatePackResult(false, message, null); }
    }
    public record FilterCreateRequest(String id, String type, float defaultStrength,
                                      Map<String, String> names, Map<String, String> importedFiles) {
        public FilterCreateRequest {
            names = names == null ? Map.of() : Map.copyOf(names);
            importedFiles = importedFiles == null ? Map.of() : Map.copyOf(importedFiles);
        }
    }
    public record FilterCreation(String id, String type, String displayNameKey,
                                 Map<String, String> files, Map<String, String> names) {
        public FilterCreation {
            files = Map.copyOf(files);
            names = names == null ? Map.of() : Map.copyOf(names);
        }
    }
    public record SaveResult(boolean success, String message, Path path, List<String> warnings) {
        public static SaveResult success(Path path, List<String> warnings) { return new SaveResult(true, "资源包已保存", path, List.copyOf(warnings)); }
        public static SaveResult failure(String message) { return new SaveResult(false, message, null, List.of()); }
    }
    public record BackupEntry(String packName, String batchName, long timestamp,
                              int fileCount, long bytes, Path path) { }
    public record BackupSnapshot(List<BackupEntry> versionBackups, List<BackupEntry> recycleBatches,
                                 List<String> legacyRecyclePacks) {
        public BackupSnapshot {
            versionBackups = List.copyOf(versionBackups);
            recycleBatches = List.copyOf(recycleBatches);
            legacyRecyclePacks = List.copyOf(legacyRecyclePacks);
        }
    }
    public record CleanupResult(int versionBackups, int recycleBatches, int legacyRecyclePacks) {
        public int total() { return versionBackups + recycleBatches + legacyRecyclePacks; }
    }
    private record Validation(boolean valid, String message, List<String> warnings) {
        static Validation ok() { return new Validation(true, "", List.of()); }
        static Validation error(String message) { return new Validation(false, message, List.of()); }
    }
}
