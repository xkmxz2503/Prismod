package com.xkmxz.prismod.client.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xkmxz.prismod.api.client.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.FilterRegistration;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.Reader;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Discovers, validates, and owns the runtime set of filters. */
public final class FilterRegistry {
    private static final FilterRegistry INSTANCE = new FilterRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<FilterKey, FilterDefinition> definitions = new LinkedHashMap<>();
    private final Map<FilterKey, String> failures = new LinkedHashMap<>();
    private final Map<RegistrationId, RegistrationRecord> registrations = new LinkedHashMap<>();
    private long generation;

    private FilterRegistry() {
        installBuiltIns();
    }

    public static FilterRegistry get() {
        return INSTANCE;
    }

    public synchronized List<FilterDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public synchronized List<FilterDefinition> cycleDefinitions() {
        return definitions.values().stream().filter(definition -> !failures.containsKey(definition.key())).toList();
    }

    public synchronized FilterDefinition definition(FilterKey key) {
        return definitions.get(key);
    }

    public synchronized String failure(FilterKey key) {
        return failures.get(key);
    }

    public synchronized boolean isAvailable(FilterKey key) {
        return definitions.containsKey(key) && !failures.containsKey(key);
    }

    public synchronized void reload(ResourceManager manager) {
        definitions.clear();
        failures.clear();
        installBuiltIns();
        Map<ResourceLocation, Resource> resources = manager.listResources("shaders/post",
                FilterRegistry::isDiscoverableResource);
        resources.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            Resource resource = resources.get(id);
            if (resource == null || !PrismodPackLoader.isPrismodPackId(resource.sourcePackId())) return;
            String packNamespace = PrismodPackLoader.namespaceForPackId(resource.sourcePackId());
            FilterDefinition definition = inspect(manager, id, false, null, packNamespace);
            if (definition != null && !definitions.containsKey(definition.key())) {
                definitions.put(definition.key(), definition);
            }
        });
        for (RegistrationRecord record : registrations.values()) {
            FilterDefinition definition = inspect(manager, record.postEffect, false, record.metadata, null);
            if (definition != null) definitions.put(record.key(), definition);
        }
        generation++;
    }

    static boolean isDiscoverableResource(ResourceLocation id) {
        return !"minecraft".equals(id.getNamespace())
                && id.getPath().endsWith(".json")
                && !id.getPath().endsWith("/empty.json");
    }

    public synchronized FilterRegistration register(String ownerId, ResourceLocation postEffect, CustomFilterMetadata metadata) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        Objects.requireNonNull(postEffect, "postEffect");
        CustomFilterMetadata safeMetadata = metadata == null ? CustomFilterMetadata.defaults() : metadata;
        FilterKey key = FilterKey.fromPostEffect(postEffect);
        RegistrationId registrationId = new RegistrationId(ownerId, key);
        RegistrationRecord record = new RegistrationRecord(registrationId, postEffect, safeMetadata, ++generation);
        registrations.put(registrationId, record);
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        FilterDefinition definition = inspect(manager, postEffect, false, safeMetadata, null);
        if (definition != null) definitions.put(key, definition);
        return new Handle(registrationId, record.version);
    }

    public synchronized void unregister(RegistrationId id, long version) {
        RegistrationRecord current = registrations.get(id);
        if (current == null || current.version != version) return;
        registrations.remove(id);
        definitions.remove(id.key);
        failures.remove(id.key);
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager.getResource(current.postEffect).isPresent()) {
            FilterDefinition discovered = inspect(manager, current.postEffect, false, null, null);
            if (discovered != null) definitions.put(id.key, discovered);
        }
        generation++;
    }

    public synchronized void markFailed(FilterKey key, Throwable error) {
        if (!definitions.containsKey(key)) return;
        failures.put(key, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        LOGGER.error("Prismod filter {} disabled after shader failure: {}", key.serializedName(), failures.get(key));
    }

    public synchronized void clearFailure(FilterKey key) {
        failures.remove(key);
    }

    private void installBuiltIns() {
        for (FilterId id : FilterId.values()) {
            ResourceLocation post = id == FilterId.ORIGINAL ? null
                    : ResourceLocation.fromNamespaceAndPath("prismod", "shaders/post/" + id.serializedName() + ".json");
            definitions.put(FilterKey.of(id), new FilterDefinition(FilterKey.of(id), post,
                    id.translationKey(), 1.0F, true));
        }
    }

    private FilterDefinition inspect(ResourceManager manager, ResourceLocation postEffect,
                                    boolean builtIn, CustomFilterMetadata metadata, String packNamespace) {
        if (postEffect == null) return null;
        boolean requirePrismodSource = !builtIn && metadata == null;
        try {
            Resource resource = manager.getResource(postEffect).orElse(null);
            if (resource == null) throw new IllegalArgumentException("missing post resource");
            if (requirePrismodSource && !PrismodPackLoader.isPrismodPackId(resource.sourcePackId())) {
                throw new IllegalArgumentException("post resource is not from Prismod resource packs");
            }
            JsonObject post = parse(resource);
            JsonArray targets = post.getAsJsonArray("targets");
            JsonArray passes = post.getAsJsonArray("passes");
            if (targets == null || targets.size() != 1 || !"swap".equals(targets.get(0).getAsString())
                    || passes == null || passes.size() != 1) {
                throw new IllegalArgumentException("requires one swap target and one pass");
            }
            JsonObject pass = passes.get(0).getAsJsonObject();
            if (!"minecraft:main".equals(pass.get("intarget").getAsString())
                    || !"swap".equals(pass.get("outtarget").getAsString())
                    || !hasUniform(pass.getAsJsonArray("uniforms"), "Intensity")) {
                throw new IllegalArgumentException("pass must be minecraft:main -> swap with Intensity");
            }
            ResourceLocation programId = resourceId(pass.get("name").getAsString(), "shaders/program", ".json");
            Resource programResource = manager.getResource(programId).orElse(null);
            if (programResource == null) throw new IllegalArgumentException("missing program resource " + programId);
            if (requirePrismodSource && !PrismodPackLoader.isPrismodPackId(programResource.sourcePackId())) {
                throw new IllegalArgumentException("program resource is not from Prismod resource packs");
            }
            if (packNamespace != null && !packNamespace.equals(
                    PrismodPackLoader.namespaceForPackId(programResource.sourcePackId()))) {
                throw new IllegalArgumentException("program resource comes from a different resource pack");
            }
            JsonObject program = parse(programResource);
            if (!hasFloatUniform(program.getAsJsonArray("uniforms"), "Intensity")) {
                throw new IllegalArgumentException("program is missing Intensity");
            }
            FilterKey key = FilterKey.fromPostEffect(postEffect);
            String translationKey = metadata != null && metadata.translationKey() != null
                    ? metadata.translationKey() : generatedTranslationKey(key);
            float strength = metadata == null ? 1.0F : metadata.defaultStrength();
            return new FilterDefinition(key, postEffect, translationKey, strength, builtIn, packNamespace);
        } catch (Exception exception) {
            FilterKey key = FilterKey.fromPostEffect(postEffect);
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            failures.put(key, reason);
            LOGGER.warn("Ignoring invalid Prismod filter {}: {}", postEffect, failures.get(key));
            String translationKey = metadata != null && metadata.translationKey() != null
                    ? metadata.translationKey() : generatedTranslationKey(key);
            float strength = metadata == null ? 1.0F : metadata.defaultStrength();
            return new FilterDefinition(key, postEffect, translationKey, strength, builtIn, packNamespace);
        }
    }

    private static JsonObject parse(Resource resource) throws Exception {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean hasUniform(JsonArray uniforms, String name) {
        if (uniforms == null) return false;
        for (JsonElement element : uniforms) {
            if (name.equals(element.getAsJsonObject().get("name").getAsString())) return true;
        }
        return false;
    }

    private static boolean hasFloatUniform(JsonArray uniforms, String name) {
        if (uniforms == null) return false;
        for (JsonElement element : uniforms) {
            JsonObject uniform = element.getAsJsonObject();
            if (name.equals(uniform.get("name").getAsString())) {
                String type = uniform.has("type") ? uniform.get("type").getAsString() : "float";
                return "float".equalsIgnoreCase(type);
            }
        }
        return false;
    }

    private static ResourceLocation resourceId(String value, String prefix, String suffix) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) throw new IllegalArgumentException("invalid resource id " + value);
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), prefix + "/" + parsed.getPath() + suffix);
    }

    private static String generatedTranslationKey(FilterKey key) {
        return "filter." + key.id().getNamespace() + "." + key.id().getPath().replace('/', '.');
    }

    public record RegistrationId(String ownerId, FilterKey key) { }

    private record RegistrationRecord(RegistrationId id, ResourceLocation postEffect,
                                     CustomFilterMetadata metadata, long version) {
        private FilterKey key() { return id.key(); }
    }

    private final class Handle implements FilterRegistration {
        private final RegistrationId id;
        private final long version;
        private boolean closed;

        private Handle(RegistrationId id, long version) {
            this.id = id;
            this.version = version;
        }

        @Override
        public ResourceLocation id() {
            return id.key().id();
        }

        @Override
        public void close() {
            synchronized (FilterRegistry.this) {
                if (!closed) {
                    closed = true;
                    unregister(id, version);
                }
            }
        }
    }
}
