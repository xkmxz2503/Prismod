package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Thread-confined state machine with an immutable cross-thread snapshot. */
final class FilterController {
    private FilterKey selected = FilterKey.of(FilterId.ORIGINAL);
    private boolean enabled = true;
    private boolean renderAvailable = true;
    private List<FilterKey> cycleOrder = defaultKeys();
    private final Map<FilterKey, Float> strengths = new HashMap<>();
    private final Map<Long, OverrideState> overrides = new LinkedHashMap<>();
    private long nextOverrideId;
    private long nextSequence;
    private long legacyOverrideId;
    private long generation;
    private Set<FilterKey> visibleKeys = Set.copyOf(defaultKeys());
    private volatile Snapshot snapshot;

    FilterController() {
        for (FilterId id : FilterId.values()) strengths.put(FilterKey.of(id), 1.0F);
        publish();
    }

    FilterSelection selectedSelection() {
        return snapshot.selected();
    }

    FilterSelection effectiveSelection() {
        return snapshot.effective();
    }

    boolean isForced() {
        return activeOverride() != null;
    }

    boolean isRenderAvailable() {
        return renderAvailable;
    }

    void select(FilterId id) {
        select(id == null ? null : FilterKey.of(id));
    }

    void select(FilterKey key) {
        selected = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
        publish();
    }

    void select(FilterKey key, float strength) {
        FilterKey normalized = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
        strengths.put(normalized, FilterStrength.normalize(strength));
        select(normalized);
    }

    void cycle() {
        OverrideState forced = activeOverride();
        if (forced != null && (forced.key().isOriginal() || FilterRegistry.get().isAvailable(forced.key()))) return;
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
        if (legacyOverrideId != 0) overrides.remove(legacyOverrideId);
        legacyOverrideId = addOverride("legacy", key, strength, 0);
    }

    void clearForced() {
        overrides.clear();
        legacyOverrideId = 0;
        publish();
    }

    long addOverride(String ownerId, FilterKey key, float strength, int priority) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        long id = ++nextOverrideId;
        overrides.put(id, new OverrideState(id, ownerId.trim(), key, strength, priority, ++nextSequence));
        publish();
        return id;
    }

    boolean removeOverride(long id) {
        boolean removed = overrides.remove(id) != null;
        if (removed) {
            if (legacyOverrideId == id) legacyOverrideId = 0;
            publish();
        }
        return removed;
    }

    int removeOverridesByOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return 0;
        int before = overrides.size();
        overrides.values().removeIf(value -> value.ownerId().equals(ownerId));
        if (before != overrides.size()) publish();
        return before - overrides.size();
    }

    String activeOverrideOwner() {
        OverrideState active = activeOverride();
        return active == null ? "" : active.ownerId();
    }

    int activeOverridePriority() {
        OverrideState active = activeOverride();
        return active == null ? 0 : active.priority();
    }

    long generation() {
        return generation;
    }

    com.xkmxz.prismod.api.common.state.FilterFallbackReason fallbackReason() {
        if (!renderAvailable) return com.xkmxz.prismod.api.common.state.FilterFallbackReason.RENDER_UNAVAILABLE;
        if (activeOverride() != null && !isSelectable(activeOverride().key())
                && !activeOverride().key().isOriginal()) {
            return com.xkmxz.prismod.api.common.state.FilterFallbackReason.UNAVAILABLE;
        }
        if (!enabled) return com.xkmxz.prismod.api.common.state.FilterFallbackReason.DISABLED;
        if (!selected.isOriginal() && !isSelectable(selected)) {
            return com.xkmxz.prismod.api.common.state.FilterFallbackReason.UNAVAILABLE;
        }
        return com.xkmxz.prismod.api.common.state.FilterFallbackReason.NONE;
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
                    : FilterStrength.normalize(value.doubleValue()));
        }
        if (selected == null) selected = FilterKey.of(FilterId.ORIGINAL);
        publish();
    }

    void setRenderAvailable(boolean available) {
        renderAvailable = available;
        publish();
    }

    void resetSession() {
        overrides.clear();
        legacyOverrideId = 0;
        publish();
    }

    private void publish() {
        FilterSelection selectedState = new FilterSelection(selected, strength(selected), false);
        OverrideState forced = activeOverride();
        FilterSelection forcedState = forced == null ? null
                : new FilterSelection(forced.key(), forced.strength(), true);
        FilterSelection effective = forcedState != null
                && (forcedState.key().isOriginal() || isSelectable(forcedState.key())) ? forcedState
                : enabled && (selected.isOriginal() || isSelectable(selected)) ? selectedState
                : new FilterSelection(FilterKey.of(FilterId.ORIGINAL), 0.0F, false);
        if (!renderAvailable) effective = new FilterSelection(FilterKey.of(FilterId.ORIGINAL), 0.0F,
                forced != null);
        snapshot = new Snapshot(selectedState, effective);
        generation++;
    }

    private OverrideState activeOverride() {
        return overrides.values().stream()
                .max(Comparator.comparingInt(OverrideState::priority).thenComparingLong(OverrideState::sequence))
                .orElse(null);
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

    private record Snapshot(FilterSelection selected, FilterSelection effective) {
    }

    private record OverrideState(long id, String ownerId, FilterKey key, float strength,
                                 int priority, long sequence) {
        private OverrideState {
            key = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
            strength = FilterStrength.normalize(strength);
        }
    }
}
