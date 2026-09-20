package com.xkmxz.prismod.client.filter.registry;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/** Validated contents of one v1 filter.json file. */
public record FilterManifest(
        FilterType type,
        String displayName,
        String source,
        float defaultStrength,
        String preview,
        String colorSpace
) {
    public static FilterManifest parse(JsonObject object) {
        if (object == null || !string(object, "schema").equals("prismod.filter")) {
            throw new IllegalArgumentException("schema must be prismod.filter");
        }
        if (!object.has("format_version") || object.get("format_version").getAsInt() != 1) {
            throw new IllegalArgumentException("unsupported filter format_version");
        }
        FilterType type = FilterType.parse(string(object, "type"));
        if (type == null) throw new UnknownFilterTypeException(string(object, "type"));
        String displayName = string(object, "display_name");
        String source = string(object, "source").replace('\\', '/');
        validateRelative(source, "source");
        float strength = object.has("default_strength") ? object.get("default_strength").getAsFloat() : 1.0F;
        if (!Float.isFinite(strength) || strength < 0.0F || strength > 1.0F) {
            throw new IllegalArgumentException("default_strength must be between 0 and 1");
        }
        String preview = object.has("preview") ? object.get("preview").getAsString().replace('\\', '/') : null;
        if (preview != null) validateRelative(preview, "preview");
        String colorSpace = null;
        if (type == FilterType.LUT3D) {
            if (!source.toLowerCase(java.util.Locale.ROOT).endsWith(".cube")) throw new IllegalArgumentException("lut3d source must be an Adobe .cube file");
            colorSpace = object.has("color_space") ? object.get("color_space").getAsString() : null;
            if (!"srgb".equalsIgnoreCase(colorSpace)) throw new IllegalArgumentException("lut3d color_space must be srgb");
        } else if (!source.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("post_chain source must be JSON");
        }
        return new FilterManifest(type, displayName, source, strength, preview, colorSpace);
    }

    private static String string(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive() || !object.getAsJsonPrimitive(field).isString()) {
            throw new IllegalArgumentException("missing " + field);
        }
        String value = object.get(field).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("blank " + field);
        return value;
    }

    private static void validateRelative(String value, String field) {
        if (value.isBlank() || value.startsWith("/") || value.contains("..") || value.contains("//") || ResourceLocation.tryParse("x:" + value) == null) {
            throw new IllegalArgumentException(field + " must be a local relative path");
        }
    }

    public static final class UnknownFilterTypeException extends IllegalArgumentException {
        public UnknownFilterTypeException(String type) { super("unknown filter type: " + type); }
    }
}
