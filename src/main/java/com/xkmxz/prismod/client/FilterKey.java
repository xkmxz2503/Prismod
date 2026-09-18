package com.xkmxz.prismod.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable identifier used by both built-in and resource-pack filters. */
public record FilterKey(ResourceLocation id) {
    public FilterKey {
        Objects.requireNonNull(id, "id");
    }

    public static FilterKey of(FilterId id) {
        return new FilterKey(ResourceLocation.fromNamespaceAndPath("prismod", id.serializedName()));
    }

    public static FilterKey parse(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.contains(":")) {
            FilterId legacy = FilterId.fromSerialized(value);
            if (legacy != null) return of(legacy);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed != null) return new FilterKey(parsed);
        FilterId legacy = FilterId.fromSerialized(value);
        return legacy == null ? null : of(legacy);
    }

    public static FilterKey fromPostEffect(ResourceLocation postEffect) {
        String path = postEffect.getPath();
        if (path.startsWith("shaders/post/") && path.endsWith(".json")) {
            path = path.substring("shaders/post/".length(), path.length() - ".json".length());
        }
        return new FilterKey(ResourceLocation.fromNamespaceAndPath(postEffect.getNamespace(), path));
    }

    public String serializedName() {
        return id.toString();
    }

    public boolean isOriginal() {
        return this.equals(of(FilterId.ORIGINAL));
    }
}
