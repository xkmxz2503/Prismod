package com.xkmxz.prismod.client.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resource-pack editor draft shared by the normal and advanced editor pages. */
public final class ResourcePackEditorDraft {
    private final ResourcePackEditorService.Session session;
    private final Map<String, String> files;
    private final Map<String, String> deletedFiles = new LinkedHashMap<>();
    private String name;
    private String namespace;

    private ResourcePackEditorDraft(ResourcePackEditorService.Session session) {
        this.session = session;
        this.files = new LinkedHashMap<>(session.files());
        this.name = session.metadata().name() == null ? "" : session.metadata().name();
        this.namespace = session.metadata().namespace();
    }

    public static ResourcePackEditorDraft open(PrismodPackLoader.PackCandidate candidate) throws java.io.IOException {
        return new ResourcePackEditorDraft(ResourcePackEditorService.open(candidate));
    }

    public ResourcePackEditorService.Session session() { return session; }
    public Map<String, String> files() { return files; }
    public String name() { return name; }
    public String namespace() { return namespace; }
    public void setName(String value) { name = value == null ? "" : value; }
    public void setNamespace(String value) {
        String next = value == null ? "" : value.trim();
        if (next.equals(namespace) || !net.minecraft.resources.ResourceLocation.isValidNamespace(next)) {
            return;
        }
        String oldPrefix = "assets/" + namespace + "/";
        String newPrefix = "assets/" + next + "/";
        Map<String, String> rewritten = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey().startsWith(oldPrefix)
                    ? newPrefix + entry.getKey().substring(oldPrefix.length()) : entry.getKey();
            rewritten.put(path, entry.getValue()
                    .replace("assets/" + namespace + "/", newPrefix)
                    .replace("filter." + namespace + ".", "filter." + next + "."));
        }
        files.clear();
        files.putAll(rewritten);
        namespace = next;
        JsonObject manifest = manifest();
        manifest.addProperty("namespace", namespace);
        if (manifest.has("filters")) {
            for (var element : manifest.getAsJsonArray("filters")) {
                JsonObject item = element.getAsJsonObject();
                if (item.has("path")) item.addProperty("path", item.get("path").getAsString().replace(oldPrefix, newPrefix));
            }
        }
        writeManifest(manifest);
    }

    public JsonObject manifest() {
        String text = files.get(PrismodPackLoader.MANIFEST_FILE);
        if (text == null) return new JsonObject();
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public void writeManifest(JsonObject manifest) {
        files.put(PrismodPackLoader.MANIFEST_FILE,
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
    }

    public List<PrismodPackLoader.PackFilterEntry> filters() {
        return PrismodPackLoader.parseManifest(manifest()).filters();
    }

    public String filterFile(String id) {
        return filters().stream().filter(entry -> entry.id().equals(id))
                .findFirst().map(entry -> entry.path() + "/filter.json").orElse(null);
    }

    public JsonObject filterManifest(String id) {
        String path = filterFile(id);
        if (path == null || !files.containsKey(path)) return new JsonObject();
        return JsonParser.parseString(files.get(path)).getAsJsonObject();
    }

    public void writeFilterManifest(String id, JsonObject filter) {
        String path = filterFile(id);
        if (path != null) files.put(path, new GsonBuilder().setPrettyPrinting().create().toJson(filter));
    }

    public void renameFilter(String oldId, String newId) {
        String oldPath = filterFile(oldId);
        if (oldPath == null) return;
        String oldPrefix = oldPath.substring(0, oldPath.length() - "/filter.json".length()) + "/";
        String newPrefix = "assets/" + namespace + "/filters/" + newId + "/";
        Map<String, String> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey().startsWith(oldPrefix)
                    ? newPrefix + entry.getKey().substring(oldPrefix.length()) : entry.getKey();
            renamed.put(path, entry.getValue()
                    .replace("filter." + namespace + "." + oldId, "filter." + namespace + "." + newId));
        }
        files.clear();
        files.putAll(renamed);
        JsonObject manifest = manifest();
        JsonArray filters = manifest.getAsJsonArray("filters");
        for (var element : filters) {
            JsonObject item = element.getAsJsonObject();
            if (oldId.equals(item.get("id").getAsString())) {
                item.addProperty("id", newId);
                item.addProperty("path", newPrefix.substring(0, newPrefix.length() - 1));
            }
        }
        writeManifest(manifest);
    }

    public void deleteFilter(String id) {
        String prefix = "assets/" + namespace + "/filters/" + id + "/";
        for (String path : files.keySet().stream().filter(value -> value.startsWith(prefix)).toList()) {
            deletedFiles.put(path, files.remove(path));
        }
        JsonObject manifest = manifest();
        JsonArray filters = manifest.getAsJsonArray("filters");
        for (int index = filters.size() - 1; index >= 0; index--) {
            if (id.equals(filters.get(index).getAsJsonObject().get("id").getAsString())) filters.remove(index);
        }
        writeManifest(manifest);
    }

    public ResourcePackEditorService.FilterCreation addFilter(String type, String id) throws java.io.IOException {
        return addFilter(new ResourcePackEditorService.FilterCreateRequest(id, type, 1.0F, Map.of(), Map.of()));
    }

    public ResourcePackEditorService.FilterCreation addFilter(ResourcePackEditorService.FilterCreateRequest request) throws java.io.IOException {
        if (request == null || request.id() == null || request.id().isBlank()) throw new java.io.IOException("滤镜 ID 不能为空");
        if (filters().stream().anyMatch(filter -> filter.id().equals(request.id()))) {
            throw new java.io.IOException("滤镜 ID 已存在：" + request.id());
        }
        ResourcePackEditorService.FilterCreation creation = ResourcePackEditorService.createFilter(session, request);
        String oldNamespace = session.metadata().namespace();
        for (Map.Entry<String, String> entry : creation.files().entrySet()) {
            String path = entry.getKey().replace("assets/" + oldNamespace + "/", "assets/" + namespace + "/");
            String value = entry.getValue().replace("filter." + oldNamespace + ".", "filter." + namespace + ".");
            files.put(path, value);
        }
        JsonObject manifest = manifest();
        JsonArray filters = manifest.getAsJsonArray("filters");
        JsonObject item = new JsonObject();
        item.addProperty("id", request.id());
        item.addProperty("path", "assets/" + namespace + "/filters/" + request.id());
        filters.add(item);
        writeManifest(manifest);
        String key = creation.displayNameKey().replace("filter." + oldNamespace + ".", "filter." + namespace + ".");
        Map<String, String> names = creation.names();
        for (Map.Entry<String, String> name : names.entrySet()) {
            if (name.getKey() == null || name.getKey().isBlank()) continue;
            String language = name.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!language.matches("[a-z]{2}_[a-z]{2}")) continue;
            String path = "assets/" + namespace + "/lang/" + language + ".json";
            JsonObject translations = languageObject(path);
            if (name.getValue() == null || name.getValue().isBlank()) translations.remove(key);
            else translations.addProperty(key, name.getValue());
            files.put(path, new GsonBuilder().setPrettyPrinting().create().toJson(translations));
        }
        return creation;
    }

    private JsonObject languageObject(String path) {
        String text = files.get(path);
        if (text == null || text.isBlank()) return new JsonObject();
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("语言文件 JSON 无效：" + path, exception);
        }
    }

    public Map<String, String> pendingFiles() {
        Map<String, String> pending = new LinkedHashMap<>(files);
        String stamp = String.valueOf(System.currentTimeMillis());
        for (Map.Entry<String, String> entry : deletedFiles.entrySet()) {
            pending.put(".prismod-recycle/" + stamp + "/" + entry.getKey(), entry.getValue());
        }
        return pending;
    }

    public ResourcePackEditorService.SaveResult save() {
        return ResourcePackEditorService.save(session, name, namespace, pendingFiles());
    }
}
