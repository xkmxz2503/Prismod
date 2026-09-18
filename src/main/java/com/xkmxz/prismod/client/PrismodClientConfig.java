package com.xkmxz.prismod.client;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PrismodClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CYCLE_ORDER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_STRENGTHS;
    private static final ForgeConfigSpec.DoubleValue[] STRENGTHS = new ForgeConfigSpec.DoubleValue[FilterId.values().length];
    private static final List<String> DEFAULT_ORDER = Arrays.stream(FilterId.values()).map(FilterId::serializedName).toList();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.comment("Whether Prismod world filters are enabled.").define("enabled", true);
        CYCLE_ORDER = builder.comment("Filter cycle order; built-in short names remain supported.")
                .define("cycle_order", DEFAULT_ORDER, value -> value instanceof List<?>);
        CUSTOM_STRENGTHS = builder.comment("Custom filter strengths as namespace:path=value entries.")
                .define("custom_strengths", List.of(), value -> value instanceof List<?>);
        for (FilterId id : FilterId.values()) {
            STRENGTHS[id.ordinal()] = builder.comment("Filter strength from 0.0 to 1.0.")
                    .defineInRange("strength_" + id.serializedName(), 1.0D, 0.0D, 1.0D);
        }
        SPEC = builder.build();
    }

    private PrismodClientConfig() { }

    public static List<FilterKey> cycleOrder() {
        Object value = CYCLE_ORDER.get();
        List<FilterKey> result = new ArrayList<>();
        if (value instanceof List<?> raw) {
            for (Object item : raw) {
                if (!(item instanceof String string)) {
                    LOGGER.warn("Ignoring non-string Prismod cycle_order entry: {}", item);
                    continue;
                }
                FilterKey key = FilterKey.parse(string);
                if (key != null && !result.contains(key)) result.add(key);
                else if (key == null) LOGGER.warn("Ignoring invalid Prismod cycle_order entry: {}", string);
                else LOGGER.warn("Ignoring duplicate Prismod cycle_order entry: {}", string);
            }
        }
        if (result.isEmpty()) for (FilterId id : FilterId.values()) result.add(FilterKey.of(id));
        return List.copyOf(result);
    }

    public static void setCycleOrder(List<FilterKey> order) {
        List<String> serialized = order == null ? DEFAULT_ORDER
                : order.stream().filter(key -> key != null).map(FilterKey::serializedName).distinct().toList();
        CYCLE_ORDER.set(serialized.isEmpty() ? DEFAULT_ORDER : serialized);
    }

    public static float strength(FilterId id) { return strength(FilterKey.of(id)); }

    public static float strength(FilterKey key) {
        if (key == null) return 0.0F;
        if ("prismod".equals(key.id().getNamespace())) {
            FilterId id = FilterId.fromSerialized(key.id().getPath());
            if (id != null) return FilterState.normalizeStrength(STRENGTHS[id.ordinal()].get());
        }
        return customStrengths().getOrDefault(key, defaultStrength(key));
    }

    public static void setStrength(FilterId id, double value) { setStrength(FilterKey.of(id), value); }

    public static void setStrength(FilterKey key, double value) {
        if (key == null) return;
        float normalized = FilterState.normalizeStrength(value);
        if ("prismod".equals(key.id().getNamespace()) && FilterId.fromSerialized(key.id().getPath()) != null) {
            STRENGTHS[FilterId.fromSerialized(key.id().getPath()).ordinal()].set((double) normalized);
            return;
        }
        Map<FilterKey, Float> custom = customStrengths();
        custom.put(key, normalized);
        CUSTOM_STRENGTHS.set(custom.entrySet().stream()
                .map(entry -> entry.getKey().serializedName() + "=" + entry.getValue()).toList());
    }

    public static Map<FilterKey, Float> customStrengths() {
        Map<FilterKey, Float> result = new LinkedHashMap<>();
        Object value = CUSTOM_STRENGTHS.get();
        if (value instanceof List<?> raw) {
            for (Object item : raw) {
                if (!(item instanceof String entry)) {
                    LOGGER.warn("Ignoring non-string Prismod custom_strengths entry: {}", item);
                    continue;
                }
                int separator = entry.lastIndexOf('=');
                if (separator <= 0 || separator == entry.length() - 1) {
                    LOGGER.warn("Ignoring invalid Prismod custom strength entry: {}", entry);
                    continue;
                }
                FilterKey key = FilterKey.parse(entry.substring(0, separator));
                if (key == null || isBuiltIn(key)) {
                    LOGGER.warn("Ignoring invalid or built-in custom strength entry: {}", entry);
                    continue;
                }
                try { result.put(key, FilterState.normalizeStrength(Double.parseDouble(entry.substring(separator + 1)))); }
                catch (NumberFormatException ignored) {
                    LOGGER.warn("Ignoring invalid Prismod custom strength value: {}", entry);
                }
            }
        }
        return result;
    }

    public static void appendDiscoveredFilters() {
        List<FilterKey> configuredOrder = cycleOrder();
        List<FilterKey> order = configuredOrder.stream()
                .filter(key -> !"minecraft".equals(key.id().getNamespace()))
                .collect(Collectors.toCollection(ArrayList::new));
        boolean changed = order.size() != configuredOrder.size();
        for (FilterDefinition definition : FilterRegistry.get().definitions()) {
            if (!order.contains(definition.key())) {
                order = new ArrayList<>(order);
                order.add(definition.key());
                changed = true;
            }
        }
        if (changed) {
            CYCLE_ORDER.set(order.stream().map(FilterKey::serializedName).toList());
            SPEC.save();
        }
    }

    private static float defaultStrength(FilterKey key) {
        FilterDefinition definition = FilterRegistry.get().definition(key);
        return definition == null ? 1.0F : definition.defaultStrength();
    }

    private static boolean isBuiltIn(FilterKey key) {
        return "prismod".equals(key.id().getNamespace()) && FilterId.fromSerialized(key.id().getPath()) != null;
    }
}
