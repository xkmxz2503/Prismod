package com.xkmxz.prismod.client.config;

import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PrismodClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CYCLE_ORDER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_STRENGTHS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_PACKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HIDDEN_FILTERS;
    private static final ForgeConfigSpec.DoubleValue[] STRENGTHS = new ForgeConfigSpec.DoubleValue[FilterId.values().length];
    private static final List<String> DEFAULT_ORDER = Arrays.stream(FilterId.values()).map(FilterId::serializedName).toList();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.comment("Whether Prismod world filters are enabled.").define("enabled", true);
        CYCLE_ORDER = builder.comment("Filter cycle order; built-in short names remain supported.")
                .define("cycle_order", DEFAULT_ORDER, value -> value instanceof List<?>);
        CUSTOM_STRENGTHS = builder.comment("Custom filter strengths as namespace:path=value entries.")
                .define("custom_strengths", List.of(), value -> value instanceof List<?>);
        DISABLED_PACKS = builder.comment("Disabled Prismod resource pack namespaces.")
                .define("disabled_packs", List.of(), value -> value instanceof List<?>);
        HIDDEN_FILTERS = builder.comment("Hidden filter IDs.")
                .define("hidden_filters", List.of(), value -> value instanceof List<?>);
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
            if (id != null) return FilterStrength.normalize(STRENGTHS[id.ordinal()].get());
        }
        return customStrengths().getOrDefault(key, defaultStrength(key));
    }

    public static void setStrength(FilterId id, double value) { setStrength(FilterKey.of(id), value); }

    public static void setStrength(FilterKey key, double value) {
        if (key == null) return;
        float normalized = FilterStrength.normalize(value);
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
                try { result.put(key, FilterStrength.normalize(Double.parseDouble(entry.substring(separator + 1)))); }
                catch (NumberFormatException ignored) {
                    LOGGER.warn("Ignoring invalid Prismod custom strength value: {}", entry);
                }
            }
        }
        return result;
    }

    public static Set<String> disabledPacks() {
        return stringSet(DISABLED_PACKS.get(), "disabled_packs");
    }

    public static void setDisabledPacks(Collection<String> namespaces) {
        DISABLED_PACKS.set(normalizeStrings(namespaces));
    }

    public static boolean isPackEnabled(String namespace) {
        if (namespace == null) return false;
        try {
            return !disabledPacks().contains(namespace);
        } catch (IllegalStateException exception) {
            // Forge discovers repository packs before the client config is loaded.
            return true;
        }
    }

    public static boolean isLoaded() {
        try {
            DISABLED_PACKS.get();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public static Set<FilterKey> hiddenFilters() {
        Set<FilterKey> result = new LinkedHashSet<>();
        for (String value : stringSet(HIDDEN_FILTERS.get(), "hidden_filters")) {
            FilterKey key = FilterKey.parse(value);
            if (key != null) result.add(key);
            else LOGGER.warn("Ignoring invalid Prismod hidden filter entry: {}", value);
        }
        return Collections.unmodifiableSet(result);
    }

    public static void setHiddenFilters(Collection<FilterKey> filters) {
        List<String> serialized = filters == null ? List.of() : filters.stream()
                .filter(key -> key != null)
                .map(FilterKey::serializedName)
                .distinct()
                .toList();
        HIDDEN_FILTERS.set(serialized);
    }

    public static boolean isFilterVisible(FilterKey key) {
        return key != null && !hiddenFilters().contains(key);
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

    public static void removeHiddenAndUnavailableFromCycleOrder() {
        List<FilterKey> filtered = cycleOrder().stream()
                .filter(key -> {
                    FilterDefinition definition = FilterRegistry.get().definition(key);
                    return definition != null && (definition.builtIn()
                            || isPackEnabled(definition.packNamespace()));
                })
                .toList();
        if (!filtered.equals(cycleOrder())) {
            setCycleOrder(filtered);
            if (isLoaded()) SPEC.save();
        }
    }

    /**
     * 删除已经不再被注册表或现存资源包清单提供的自定义滤镜配置。
     * 调试预设不在这里处理：资源包暂时移除后仍应保留，待资源包重新出现时继续生效。
     */
    public static boolean pruneRemovedFilterSettings() {
        // Forge 首次扫描资源包可能早于客户端 TOML 配置加载，此时延后到下一次资源重载。
        if (!isLoaded()) return false;
        Set<FilterKey> available = knownFilterKeys();
        boolean changed = false;

        List<FilterKey> order = cycleOrder();
        List<FilterKey> prunedOrder = order.stream()
                .filter(key -> key != null && (isBuiltIn(key) || available.contains(key)))
                .toList();
        if (!prunedOrder.equals(order)) {
            setCycleOrder(prunedOrder);
            changed = true;
        }

        Map<FilterKey, Float> strengths = customStrengths();
        Map<FilterKey, Float> prunedStrengths = strengths.entrySet().stream()
                .filter(entry -> available.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (first, ignored) -> first, LinkedHashMap::new));
        if (!prunedStrengths.equals(strengths)) {
            CUSTOM_STRENGTHS.set(prunedStrengths.entrySet().stream()
                    .map(entry -> entry.getKey().serializedName() + "=" + entry.getValue())
                    .toList());
            changed = true;
        }

        Set<FilterKey> hidden = hiddenFilters();
        Set<FilterKey> prunedHidden = hidden.stream()
                .filter(key -> isBuiltIn(key) || available.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!prunedHidden.equals(hidden)) {
            setHiddenFilters(prunedHidden);
            changed = true;
        }
        return changed;
    }

    /** 当前注册表加上仍存在但暂时禁用的资源包清单中的滤镜身份。 */
    private static Set<FilterKey> knownFilterKeys() {
        Set<FilterKey> result = FilterRegistry.get().definitions().stream()
                .map(FilterDefinition::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            for (PrismodPackLoader.PackCandidate candidate : PrismodPackLoader.scan(PrismodPackLoader.resourcePacksDirectory())) {
                for (PrismodPackLoader.PackFilterEntry filter : candidate.metadata().filters()) {
                    result.add(new FilterKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            candidate.metadata().namespace(), filter.id())));
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Unable to inspect Prismod resource pack manifests while pruning config", exception);
        }
        return result;
    }

    private static float defaultStrength(FilterKey key) {
        FilterDefinition definition = FilterRegistry.get().definition(key);
        return definition == null ? 1.0F : definition.defaultStrength();
    }

    private static boolean isBuiltIn(FilterKey key) {
        return "prismod".equals(key.id().getNamespace()) && FilterId.fromSerialized(key.id().getPath()) != null;
    }

    private static Set<String> stringSet(Object value, String field) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof List<?> raw) {
            for (Object item : raw) {
                if (item instanceof String string && !string.isBlank()) result.add(string);
                else if (!(item instanceof String)) LOGGER.warn("Ignoring non-string Prismod {} entry: {}", field, item);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static List<String> normalizeStrings(Collection<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }
}
