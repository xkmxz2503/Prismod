package com.xkmxz.prismod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.filter.debug.FilterDebugSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/** 按资源包 namespace 和滤镜路径独立保存调参。 */
public final class FilterDebugPresetStore {
    public static final int FORMAT_VERSION = 1;
    public static final String SCHEMA = "prismod.filter_debug";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    private Path cachedFile;
    private long cachedModified = Long.MIN_VALUE;
    private FilterDebugSettings cachedSettings = FilterDebugSettings.defaults();

    public FilterDebugPresetStore(Path root) { this.root = root; }

    public static FilterDebugPresetStore createDefault() {
        return new FilterDebugPresetStore(FMLPaths.CONFIGDIR.get().resolve("prismod/config/resourcepacks"));
    }

    public Path file(String namespace, String filterId) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, filterId);
        return root.resolve(id.getNamespace()).resolve(id.getPath() + ".json");
    }

    public synchronized void clearCache() {
        cachedFile = null;
        cachedModified = Long.MIN_VALUE;
        cachedSettings = FilterDebugSettings.defaults();
    }

    public FilterDebugSettings get(String namespace, String filterId) {
        Path file;
        try { file = file(namespace, filterId); }
        catch (RuntimeException invalid) { return FilterDebugSettings.defaults(); }
        long modified = lastModified(file);
        if (file.equals(cachedFile) && modified == cachedModified) return cachedSettings;
        if (!Files.isRegularFile(file)) return cache(file, modified, FilterDebugSettings.defaults());
        String identity = ResourceLocation.fromNamespaceAndPath(namespace, filterId).toString();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IOException("root must be an object");
            JsonObject object = parsed.getAsJsonObject();
            if (!SCHEMA.equals(requiredString(object, "schema"))
                    || requiredInt(object, "format_version") != FORMAT_VERSION
                    || !identity.equals(requiredString(object, "filter"))) {
                throw new IOException("schema, version or filter identity mismatch");
            }
            JsonElement settings = object.get("settings");
            if (settings == null || !settings.isJsonObject()) throw new IOException("missing settings");
            return cache(file, modified, FilterDebugSettings.fromJson(settings.getAsJsonObject()));
        } catch (Exception exception) {
            LOGGER.warn("Unable to read Prismod filter debug preset {}; using defaults", file, exception);
            return cache(file, modified, FilterDebugSettings.defaults());
        }
    }

    public void put(String namespace, String filterId, FilterDebugSettings settings) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, filterId);
        Path file = file(namespace, filterId);
        Files.createDirectories(file.toAbsolutePath().getParent());
        JsonObject object = new JsonObject();
        object.addProperty("schema", SCHEMA);
        object.addProperty("format_version", FORMAT_VERSION);
        object.addProperty("filter", id.toString());
        object.add("settings", (settings == null ? FilterDebugSettings.defaults() : settings).toJson());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(object, writer); }
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException atomicFailure) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        cachedFile = file;
        cachedModified = lastModified(file);
        cachedSettings = settings == null ? FilterDebugSettings.defaults() : settings;
    }

    private FilterDebugSettings cache(Path file, long modified, FilterDebugSettings settings) {
        cachedFile = file;
        cachedModified = modified;
        cachedSettings = settings;
        return settings;
    }

    private static long lastModified(Path file) {
        try { return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).to(TimeUnit.NANOSECONDS) : -1L; }
        catch (IOException ignored) { return -1L; }
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) throw new IOException("missing " + name);
        return object.get(name).getAsString();
    }

    private static int requiredInt(JsonObject object, String name) throws IOException {
        try { return object.get(name).getAsInt(); }
        catch (Exception exception) { throw new IOException("missing " + name, exception); }
    }
}
