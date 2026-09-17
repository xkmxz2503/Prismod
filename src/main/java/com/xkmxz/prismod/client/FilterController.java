package com.xkmxz.prismod.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 纯 Java 状态机；写入由客户端线程串行执行，读取通过不可变快照发布。 */
final class FilterController {
    private FilterId selected = FilterId.ORIGINAL;
    private FilterState forcedState;
    private boolean enabled = true;
    private boolean renderAvailable = true;
    private List<FilterId> cycleOrder = FilterId.defaultOrder();
    private final EnumMap<FilterId, Float> strengths = new EnumMap<>(FilterId.class);
    private volatile Snapshot snapshot;

    FilterController() {
        for (FilterId id : FilterId.values()) strengths.put(id, 1.0F);
        publish();
    }

    FilterState selectedState() {
        return snapshot.selected();
    }

    FilterState effectiveState() {
        return snapshot.effective();
    }

    boolean isForced() {
        return snapshot.effective().forced();
    }

    void select(FilterId id) {
        selected = id == null ? FilterId.ORIGINAL : id;
        publish();
    }

    void cycle() {
        if (forcedState != null) return;
        selected = cycleOrder.get((cycleOrder.indexOf(selected) + 1) % cycleOrder.size());
        publish();
    }

    void setForced(FilterId id, float strength) {
        forcedState = new FilterState(id, strength, true);
        publish();
    }

    void clearForced() {
        forcedState = null;
        publish();
    }

    void refreshConfig(boolean enabled, List<FilterId> order, Map<FilterId, ? extends Number> configuredStrengths) {
        this.enabled = enabled;
        if (order != null && order.size() == FilterId.values().length
                && order.stream().noneMatch(Objects::isNull)
                && order.stream().distinct().count() == FilterId.values().length) {
            cycleOrder = List.copyOf(order);
        } else {
            cycleOrder = FilterId.defaultOrder();
        }
        for (FilterId id : FilterId.values()) {
            Number value = configuredStrengths.get(id);
            strengths.put(id, value == null ? 1.0F : FilterState.normalizeStrength(value.doubleValue()));
        }
        publish();
    }

    void setRenderAvailable(boolean available) {
        renderAvailable = available;
        publish();
    }

    /** 离开世界时只移除本次会话的覆盖，保留用户自己的滤镜选择。 */
    void resetSession() {
        clearForced();
    }

    private void publish() {
        FilterState selectedState = new FilterState(selected, strengths.get(selected), false);
        FilterState effective = forcedState != null ? forcedState
                : enabled ? selectedState : new FilterState(FilterId.ORIGINAL, 0.0F, false);
        if (!renderAvailable) effective = new FilterState(FilterId.ORIGINAL, 0.0F, forcedState != null);
        snapshot = new Snapshot(selectedState, effective);
    }

    private record Snapshot(FilterState selected, FilterState effective) {
    }
}
