package com.xkmxz.prismod.client.filter;

import java.util.Locale;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

public enum FilterId {
    ORIGINAL("original"),
    GRAYSCALE("grayscale"),
    WARM("warm"),
    COOL("cool"),
    VINTAGE("vintage"),
    NIGHT_VISION("night_vision");

    private final String serializedName;

    FilterId(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "filter.prismod." + serializedName;
    }

    public static FilterId fromSerialized(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (FilterId id : values()) {
            if (id.serializedName.equals(normalized)) return id;
        }
        return null;
    }

    public static List<FilterId> defaultOrder() {
        return List.of(values());
    }

    /** 保留原始列表中的所有元素；任一非法元素都会使整份顺序回退。 */
    public static List<FilterId> parseCycleOrder(Object value, Consumer<String> warning) {
        if (value instanceof List<?> raw && raw.size() == values().length) {
            List<FilterId> result = new ArrayList<>(raw.size());
            EnumSet<FilterId> seen = EnumSet.noneOf(FilterId.class);
            for (Object item : raw) {
                FilterId id = item instanceof String name ? fromSerialized(name) : null;
                if (id == null || !seen.add(id)) return invalidOrder(warning);
                result.add(id);
            }
            return List.copyOf(result);
        }
        return invalidOrder(warning);
    }

    private static List<FilterId> invalidOrder(Consumer<String> warning) {
        warning.accept("Prismod 的 cycle_order 必须恰好包含六个不同的有效滤镜 ID，已回退为默认顺序。");
        return defaultOrder();
    }
}
