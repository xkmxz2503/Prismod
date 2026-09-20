package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Thread-confined state machine with an immutable cross-thread snapshot. */
final class FilterController {
    private FilterKey selected = FilterKey.of(FilterId.ORIGINAL);
    private FilterSelection forcedState;
    private boolean enabled = true;
    private boolean renderAvailable = true;
    private List<FilterKey> cycleOrder = defaultKeys();
    private final Map<FilterKey, Float> strengths = new HashMap<>();
    private Set<FilterKey> visibleKeys = Set.copyOf(defaultKeys());
    private volatile Snapshot snapshot;

    FilterController() {
        for (FilterId id : FilterId.values()) strengths.put(FilterKey.of(id), 1.0F);
        publish();
    }

    FilterState selectedState() {
        return legacyState(snapshot.selected());
    }

    FilterState effectiveState() {
        return legacyState(snapshot.effective());
    }

    FilterSelection selectedSelection() {
        return snapshot.selected();
    }

    FilterSelection effectiveSelection() {
        return snapshot.effective();
    }

    boolean isForced() {
        return snapshot.effective().forced();
    }

    void select(FilterId id) {
        select(id == null ? null : FilterKey.of(id));
    }

    void select(FilterKey key) {
        selected = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
        publish();
    }

    void cycle() {
        if (forcedState != null && (forcedState.key().isOriginal()
                || FilterRegistry.get().isAvailable(forcedState.key()))) return;
        List<FilterKey> available = cycleOrder.stream().filter(this::isSelectable).toList();
        if (available.isEmpty()) {
            selected = FilterKey.of(FilterId.ORIGINAL);
        } else {
            int index = available.indexOf(selected);
            selected = available.get((index + 1) % available.size());
        }
        publish();
    }

    void setForced(FilterId id, float strength) {
        setForced(id == null ? null : FilterKey.of(id), strength);
    }

    void setForced(FilterKey key, float strength) {
        forcedState = new FilterSelection(key, strength, true);
        publish();
    }

    void clearForced() {
        forcedState = null;
        publish();
    }

    void refreshConfig(boolean enabled, List<FilterId> order, Map<FilterId, ? extends Number> configuredStrengths) {
        List<FilterKey> keys = order == null ? null : order.stream().map(FilterKey::of).toList();
        Map<FilterKey, Number> strengths = new HashMap<>();
        if (configuredStrengths != null) {
            configuredStrengths.forEach((key, value) -> strengths.put(FilterKey.of(key), value));
        }
        refreshDynamicConfig(enabled, keys, strengths);
    }

    void refreshDynamicConfig(boolean enabled, List<FilterKey> order,
                              Map<FilterKey, ? extends Number> configuredStrengths) {
        refreshDynamicConfig(enabled, order, configuredStrengths, null);
    }

    void refreshDynamicConfig(boolean enabled, List<FilterKey> order,
                              Map<FilterKey, ? extends Number> configuredStrengths,
                              Set<FilterKey> visibleKeys) {
        this.enabled = enabled;
        LinkedHashSet<FilterKey> normalized = new LinkedHashSet<>();
        if (order != null) normalized.addAll(order);
        for (FilterDefinition definition : FilterRegistry.get().definitions()) normalized.add(definition.key());
        if (normalized.isEmpty()) normalized.addAll(defaultKeys());
        cycleOrder = List.copyOf(normalized);
        if (visibleKeys == null) {
            this.visibleKeys = Set.copyOf(normalized);
        } else {
            this.visibleKeys = Set.copyOf(visibleKeys);
        }
        for (FilterDefinition definition : FilterRegistry.get().definitions()) {
            Number value = configuredStrengths == null ? null : configuredStrengths.get(definition.key());
            strengths.put(definition.key(), value == null ? definition.defaultStrength()
                    : FilterState.normalizeStrength(value.doubleValue()));
        }
        if (selected == null) selected = FilterKey.of(FilterId.ORIGINAL);
        publish();
    }

    void setRenderAvailable(boolean available) {
        renderAvailable = available;
        publish();
    }

    void resetSession() {
        clearForced();
    }

    private void publish() {
        FilterSelection selectedState = new FilterSelection(selected, strength(selected), false);
        FilterSelection effective = forcedState != null
                && (forcedState.key().isOriginal() || isSelectable(forcedState.key())) ? forcedState
                : enabled && (selected.isOriginal() || isSelectable(selected)) ? selectedState
                : new FilterSelection(FilterKey.of(FilterId.ORIGINAL), 0.0F, false);
        if (!renderAvailable) effective = new FilterSelection(FilterKey.of(FilterId.ORIGINAL), 0.0F,
                forcedState != null);
        snapshot = new Snapshot(selectedState, effective);
    }

    private float strength(FilterKey key) {
        Float value = strengths.get(key);
        return value == null ? 1.0F : value;
    }

    private boolean isSelectable(FilterKey key) {
        return key != null && visibleKeys.contains(key) && FilterRegistry.get().isAvailable(key);
    }

    private static List<FilterKey> defaultKeys() {
        List<FilterKey> keys = new ArrayList<>();
        for (FilterId id : FilterId.values()) keys.add(FilterKey.of(id));
        return List.copyOf(keys);
    }

    private static FilterState legacyState(FilterSelection selection) {
        return new FilterState(toLegacyId(selection.key()), selection.strength(), selection.forced());
    }

    private static FilterId toLegacyId(FilterKey key) {
        if (key != null && "prismod".equals(key.id().getNamespace())) {
            return FilterId.fromSerialized(key.id().getPath());
        }
        return FilterId.ORIGINAL;
    }

    private record Snapshot(FilterSelection selected, FilterSelection effective) {
    }
}
