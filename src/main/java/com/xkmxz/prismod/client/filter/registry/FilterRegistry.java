package com.xkmxz.prismod.client.filter.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.api.client.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.FilterRegistration;
import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.client.filter.lut.Lut3dData;
import com.xkmxz.prismod.client.filter.lut.LutCubeParser;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Manifest-driven registry. New filter types are isolated behind FilterManifest parsing. */
public final class FilterRegistry {
    public static final List<String> DEBUG_UNIFORMS = List.of("Intensity", "Exposure", "Contrast", "Highlights", "Shadows", "Saturation", "Temperature", "Tint", "Gamma");
    private static final FilterRegistry INSTANCE = new FilterRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<FilterKey, FilterDefinition> definitions = new LinkedHashMap<>();
    private final Map<FilterKey, String> failures = new LinkedHashMap<>();
    private final Map<RegistrationId, RegistrationRecord> registrations = new LinkedHashMap<>();
    private long generation;

    private FilterRegistry() { installBuiltIns(); }
    public static FilterRegistry get() { return INSTANCE; }
    public synchronized List<FilterDefinition> definitions() { return List.copyOf(definitions.values()); }
    public synchronized List<FilterDefinition> cycleDefinitions() { return definitions.values().stream().filter(d -> !failures.containsKey(d.key())).toList(); }
    public synchronized FilterDefinition definition(FilterKey key) { return definitions.get(key); }
    public synchronized String failure(FilterKey key) { return failures.get(key); }
    public synchronized boolean isAvailable(FilterKey key) { return definitions.containsKey(key) && !failures.containsKey(key); }

    /** @deprecated retained only for source compatibility; v1 discovery never calls this method. */
    @Deprecated
    static boolean isDiscoverableResource(ResourceLocation id) {
        return false;
    }

    public synchronized void reload(ResourceManager manager) {
        definitions.clear(); failures.clear(); installBuiltIns();
        if (manager instanceof PrismodPackLoader.PrismodResourceManager privateManager) {
            for (PrismodPackLoader.PackCandidate candidate : privateManager.candidates()) {
                String namespace = candidate.metadata().namespace();
                for (PrismodPackLoader.PackFilterEntry entry : candidate.metadata().filters()) {
                        FilterKey key = new FilterKey(ResourceLocation.fromNamespaceAndPath(namespace, entry.id()));
                    try {
                        FilterDefinition definition = loadFilter(privateManager, candidate, entry);
                        if (definition != null) definitions.put(key, definition);
                    } catch (FilterManifest.UnknownFilterTypeException unknown) {
                        LOGGER.warn("Skipping unsupported Prismod filter {}", key.serializedName());
                    } catch (Exception exception) {
                        failures.put(key, message(exception));
                        LOGGER.warn("Ignoring invalid Prismod filter {}", key.serializedName(), exception);
                    }
                }
            }
        }
        for (RegistrationRecord record : registrations.values()) {
            FilterDefinition definition = inspectPostChain(manager, record.postEffect(), record.metadata(), null);
            if (definition != null) definitions.put(record.key(), definition);
        }
        generation++;
    }

    private FilterDefinition loadFilter(PrismodPackLoader.PrismodResourceManager manager,
                                        PrismodPackLoader.PackCandidate candidate,
                                        PrismodPackLoader.PackFilterEntry entry) throws Exception {
        String namespace = candidate.metadata().namespace();
        String root = entry.relativeFilterDirectory(namespace);
        ResourceLocation manifestId = ResourceLocation.fromNamespaceAndPath(namespace, "filters/" + root + "/filter.json");
        FilterManifest manifest;
        try (Reader reader = manager.getResource(manifestId).orElseThrow().openAsReader()) {
            manifest = FilterManifest.parse(JsonParser.parseReader(reader).getAsJsonObject());
        }
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath(namespace, "filters/" + root + "/" + manifest.source());
        if (manager.getResource(source).isEmpty()) throw new IllegalArgumentException("missing filter source");
        FilterKey key = new FilterKey(ResourceLocation.fromNamespaceAndPath(namespace, entry.id()));
        if (manifest.type() == FilterType.LUT3D) {
            Lut3dData lut;
            try (Reader reader = manager.getResource(source).orElseThrow().openAsReader()) { lut = LutCubeParser.parse(reader); }
        return new FilterDefinition(key, FilterType.LUT3D,
                    ResourceLocation.fromNamespaceAndPath("prismod", "runtime/lut3d.json"),
                    source, manifest.displayName(), manifest.defaultStrength(), candidate.bundled(), namespace, lut, true);
        }
        ResourceLocation virtualPost = ResourceLocation.fromNamespaceAndPath(namespace, "shaders/post/" + entry.id() + ".json");
        manager.registerVirtualResources(Map.of(virtualPost, source));
        JsonObject post = parse(manager.getResource(source).orElseThrow());
        mapPostChain(manager, namespace, root, post);
        boolean debugSupported = validatePostChain(manager, virtualPost);
        return new FilterDefinition(key, FilterType.POST_CHAIN, virtualPost, source, manifest.displayName(), manifest.defaultStrength(), candidate.bundled(), namespace, null, debugSupported);
    }

    private static void mapPostChain(PrismodPackLoader.PrismodResourceManager manager, String namespace, String root, JsonObject post) {
        JsonArray passes = post.getAsJsonArray("passes");
        if (passes == null) return;
        for (JsonElement element : passes) {
            JsonObject pass = element.getAsJsonObject();
            if (!pass.has("name")) continue;
            ResourceLocation program = ResourceLocation.tryParse(pass.get("name").getAsString());
            if (program == null || !namespace.equals(program.getNamespace())) {
                throw new IllegalArgumentException("post_chain program must stay in the filter namespace");
            }
            ResourceLocation virtualProgram = ResourceLocation.fromNamespaceAndPath(namespace, "shaders/program/" + program.getPath() + ".json");
            ResourceLocation physicalProgram = ResourceLocation.fromNamespaceAndPath(namespace, "filters/" + root + "/program/" + program.getPath() + ".json");
            manager.registerVirtualResources(Map.of(virtualProgram, physicalProgram));
            try {
                JsonObject shader = parse(manager.getResource(physicalProgram).orElseThrow());
                for (String field : List.of("vertex", "fragment")) {
                    if (!shader.has(field)) continue;
                    ResourceLocation name = ResourceLocation.tryParse(shader.get(field).getAsString());
                    if (name == null || !namespace.equals(name.getNamespace())) {
                        throw new IllegalArgumentException("post_chain shader must stay in the filter namespace");
                    }
                    manager.registerVirtualResources(Map.of(
                            ResourceLocation.fromNamespaceAndPath(namespace, "shaders/program/" + name.getPath() + ".vsh"),
                            ResourceLocation.fromNamespaceAndPath(namespace, "filters/" + root + "/program/" + name.getPath() + ".vsh"),
                            ResourceLocation.fromNamespaceAndPath(namespace, "shaders/program/" + name.getPath() + ".fsh"),
                            ResourceLocation.fromNamespaceAndPath(namespace, "filters/" + root + "/program/" + name.getPath() + ".fsh")));
                }
            } catch (Exception exception) {
                if (exception instanceof IllegalArgumentException illegalArgumentException) throw illegalArgumentException;
                throw new IllegalArgumentException("unable to map post_chain program", exception);
            }
        }
    }

    public synchronized FilterRegistration register(String ownerId, ResourceLocation postEffect, CustomFilterMetadata metadata) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        Objects.requireNonNull(postEffect, "postEffect");
        CustomFilterMetadata safe = metadata == null ? CustomFilterMetadata.defaults() : metadata;
        FilterKey key = FilterKey.fromPostEffect(postEffect);
        RegistrationRecord record = new RegistrationRecord(new RegistrationId(ownerId, key), postEffect, safe, ++generation);
        registrations.put(record.id(), record);
        FilterDefinition definition = inspectPostChain(Minecraft.getInstance().getResourceManager(), postEffect, safe, null);
        if (definition != null) definitions.put(key, definition);
        return new Handle(record.id(), record.version());
    }

    public synchronized void unregister(RegistrationId id, long version) {
        RegistrationRecord current = registrations.get(id);
        if (current == null || current.version() != version) return;
        registrations.remove(id); definitions.remove(id.key()); failures.remove(id.key()); generation++;
    }

    public synchronized void markFailed(FilterKey key, Throwable error) { if (definitions.containsKey(key)) failures.put(key, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()); }
    public synchronized void clearFailure(FilterKey key) { failures.remove(key); }

    private void installBuiltIns() {
        // 仅作为资源重载前的状态占位；首次 Prismod 重载会从内置 prismod.pack.json
        // 读取并覆盖这些定义，实际渲染资源始终来自 v1 资源包候选。
        for (FilterId id : FilterId.values()) {
            FilterKey key = FilterKey.of(id);
            definitions.put(key, new FilterDefinition(key, FilterType.POST_CHAIN, null, null,
                    id.translationKey(), 1.0F, true, null, null, false));
        }
    }

    private static FilterDefinition inspectPostChain(ResourceManager manager, ResourceLocation postEffect, CustomFilterMetadata metadata, String packNamespace) {
        if (postEffect == null) return null;
        FilterKey key = FilterKey.fromPostEffect(postEffect);
        try {
            boolean debugSupported = validatePostChain(manager, postEffect);
            String translation = metadata != null && metadata.translationKey() != null ? metadata.translationKey() : generatedTranslationKey(key);
            return new FilterDefinition(key, FilterType.POST_CHAIN, postEffect, postEffect, translation, metadata == null ? 1.0F : metadata.defaultStrength(), metadata == null, packNamespace, null, debugSupported);
        } catch (Exception exception) {
            INSTANCE.failures.put(key, message(exception));
            return new FilterDefinition(key, FilterType.POST_CHAIN, postEffect, postEffect, generatedTranslationKey(key), 1.0F, metadata == null, packNamespace, null, false);
        }
    }

    private static boolean validatePostChain(ResourceManager manager, ResourceLocation postEffect) throws Exception {
        JsonObject post = parse(manager.getResource(postEffect).orElseThrow(() -> new IllegalArgumentException("missing post resource")));
        JsonArray targets = post.getAsJsonArray("targets"); JsonArray passes = post.getAsJsonArray("passes");
        if (targets == null || targets.size() != 1 || !"swap".equals(targets.get(0).getAsString()) || passes == null || passes.size() != 1) throw new IllegalArgumentException("requires one swap target and one pass");
        JsonObject pass = passes.get(0).getAsJsonObject();
        if (!"minecraft:main".equals(pass.get("intarget").getAsString()) || !"swap".equals(pass.get("outtarget").getAsString())) throw new IllegalArgumentException("pass must be minecraft:main -> swap");
        ResourceLocation program = ResourceLocation.tryParse(pass.get("name").getAsString());
        if (program == null) throw new IllegalArgumentException("invalid program resource");
        JsonObject programJson = parse(manager.getResource(ResourceLocation.fromNamespaceAndPath(program.getNamespace(), "shaders/program/" + program.getPath() + ".json")).orElseThrow());
        boolean supported = true;
        for (String uniform : DEBUG_UNIFORMS) {
            if (!hasFloatUniform(pass.getAsJsonArray("uniforms"), uniform) || !hasFloatUniform(programJson.getAsJsonArray("uniforms"), uniform)) {
                supported = false;
                LOGGER.warn("Prismod filter {} does not support debug tuning: missing float uniform {}", postEffect, uniform);
            }
        }
        return supported;
    }

    private static JsonObject parse(Resource resource) throws Exception { try (Reader reader = resource.openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); } }
    private static boolean hasFloatUniform(JsonArray uniforms, String name) { if (uniforms == null) return false; for (JsonElement e : uniforms) { JsonObject u = e.getAsJsonObject(); if (name.equals(u.get("name").getAsString())) return u.has("type") && "float".equalsIgnoreCase(u.get("type").getAsString()); } return false; }
    private static String generatedTranslationKey(FilterKey key) { return "filter." + key.id().getNamespace() + "." + key.id().getPath().replace('/', '.'); }
    private static String message(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    public record RegistrationId(String ownerId, FilterKey key) { }
    private record RegistrationRecord(RegistrationId id, ResourceLocation postEffect, CustomFilterMetadata metadata, long version) { private FilterKey key() { return id.key(); } }
    private final class Handle implements FilterRegistration {
        private final RegistrationId id; private final long version; private boolean closed;
        private Handle(RegistrationId id, long version) { this.id = id; this.version = version; }
        @Override public ResourceLocation id() { return id.key().id(); }
        @Override public void close() { synchronized (FilterRegistry.this) { if (!closed) { closed = true; unregister(id, version); } } }
    }
}
