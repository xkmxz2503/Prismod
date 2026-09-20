package com.xkmxz.prismod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.filter.LutDebugSettings;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** 独立于 Forge TOML 的 LUT 调试预设存储。 */
public final class LutPresetStore {
    public static final int FORMAT_VERSION = 1;
    private static final String SCHEMA = "prismod.lut_presets";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, LutDebugSettings> presets = new LinkedHashMap<>();
    private boolean loaded;

    public LutPresetStore(Path file) {
        this.file = file;
    }

    public static LutPresetStore createDefault() {
        return new LutPresetStore(FMLPaths.CONFIGDIR.get().resolve("prismod/config/lut-presets.json"));
    }

    public Path file() { return file; }

    public synchronized Map<String, LutDebugSettings> load() {
        if (loaded) return Map.copyOf(presets);
        loaded = true;
        presets.clear();
        if (!Files.isRegularFile(file)) return Map.of();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IOException("preset root must be an object");
            JsonObject object = root.getAsJsonObject();
            if (!SCHEMA.equals(string(object, "schema")) || object.get("format_version").getAsInt() != FORMAT_VERSION) {
                throw new IOException("unsupported LUT preset format");
            }
            JsonElement entries = object.get("presets");
            if (entries != null && entries.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : entries.getAsJsonObject().entrySet()) {
                    if (entry.getKey().isBlank() || !entry.getValue().isJsonObject()) continue;
                    presets.put(entry.getKey(), LutDebugSettings.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception exception) {
            presets.clear();
            LOGGER.warn("Unable to read Prismod LUT presets {}; using defaults", file, exception);
        }
        return Map.copyOf(presets);
    }

    public synchronized LutDebugSettings get(String filterId) {
        load();
        return presets.getOrDefault(filterId, LutDebugSettings.defaults());
    }

    public synchronized void put(String filterId, LutDebugSettings settings) throws IOException {
        if (filterId == null || filterId.isBlank()) throw new IllegalArgumentException("filterId must not be blank");
        load();
        presets.put(filterId, settings == null ? LutDebugSettings.defaults() : settings);
        save();
    }

    public synchronized void remove(String filterId) throws IOException {
        load();
        if (presets.remove(filterId) != null) save();
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("format_version", FORMAT_VERSION);
        JsonObject entries = new JsonObject();
        presets.forEach((key, value) -> entries.add(key, value.toJson()));
        root.add("presets", entries);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String string(JsonObject object, String name) throws IOException {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) throw new IOException("missing " + name);
        return object.get(name).getAsString();
    }
}
