package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.config.PrismodClientConfig;
import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import com.xkmxz.prismod.api.client.FilterOverride;
import com.xkmxz.prismod.api.client.FilterSnapshot;
import com.xkmxz.prismod.api.client.FilterStateListener;
import com.xkmxz.prismod.api.client.FilterSubscription;
import com.xkmxz.prismod.api.common.state.FilterOverrideState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** 状态读取可跨线程；所有写操作以及配置读取只由客户端线程执行。 */
public final class FilterManager {
    private static final FilterManager INSTANCE = new FilterManager();
    private final FilterController controller = new FilterController();
    private final CopyOnWriteArrayList<FilterStateListener> listeners = new CopyOnWriteArrayList<>();

    private FilterManager() {
    }

    public static FilterManager get() {
        return INSTANCE;
    }

    public void cycle() {
        controller.cycle();
        notifyListeners();
    }

    public void select(FilterId id) {
        controller.select(id);
        notifyListeners();
    }

    public void select(FilterKey key) {
        controller.select(key);
        notifyListeners();
    }

    public void selectSession(FilterKey key, float strength) {
        controller.selectSession(key, strength);
        notifyListeners();
    }

    public void clearSessionSelection() {
        controller.clearSessionSelection();
        notifyListeners();
    }

    public void setForced(FilterKey key, float strength) {
        controller.setForced(key, strength);
        notifyListeners();
    }

    public void setForced(net.minecraft.resources.ResourceLocation id, float strength) {
        controller.setForced(new FilterKey(id), strength);
        notifyListeners();
    }

    public void clearForced() {
        controller.clearForced();
        notifyListeners();
    }

    public FilterOverride addOverride(String ownerId, FilterKey key, float strength, int priority) {
        long id = controller.addOverride(ownerId, key, strength, priority);
        notifyListeners();
        return new OverrideHandle(id, ownerId, key.id(), priority);
    }

    public int removeOverridesByOwner(String ownerId) {
        int count = controller.removeOverridesByOwner(ownerId);
        if (count > 0) notifyListeners();
        return count;
    }

    public FilterSubscription subscribe(FilterStateListener listener) {
        if (listener == null) throw new NullPointerException("listener");
        listeners.add(listener);
        return new FilterSubscription() {
            private boolean closed;
            @Override public void close() {
                if (!closed) { closed = true; listeners.remove(listener); }
            }
        };
    }

    public FilterSnapshot snapshot() {
        FilterSelection effective = controller.effectiveSelection();
        FilterSelection selected = controller.selectedSelection();
        return new FilterSnapshot(effective.key().id(), effective.strength(), effective.forced(),
                controller.isRenderAvailable(), selected.key().id(), selected.strength(),
                controller.activeOverrideOwner(), controller.activeOverridePriority(),
                controller.fallbackReason(), controller.generation());
    }

    public boolean isForced() {
        return controller.isForced();
    }

    public boolean isRenderAvailable() {
        return controller.isRenderAvailable();
    }

    /** 配置加载、热重载或界面保存后，在客户端线程刷新全部配置与状态。 */
    public void refreshConfig() {
        Map<FilterKey, Float> strengths = new HashMap<>();
        Set<FilterKey> visible = new HashSet<>();
        for (FilterDefinition definition : FilterRegistry.get().definitions()) {
            strengths.put(definition.key(), PrismodClientConfig.strength(definition.key()));
            if (PrismodClientConfig.isFilterVisible(definition.key())) visible.add(definition.key());
        }
        controller.refreshDynamicConfig(PrismodClientConfig.ENABLED.get(), PrismodClientConfig.cycleOrder(), strengths, visible);
        notifyListeners();
    }

    public FilterSelection effectiveSelection() {
        return controller.effectiveSelection();
    }

    /** 显示当前滤镜名称，保留自定义滤镜的 namespace:path 身份。 */
    public Component effectiveDisplayName() {
        FilterSelection selection = effectiveSelection();
        FilterDefinition definition = FilterRegistry.get().definition(selection.key());
        return definition == null ? Component.literal(selection.key().serializedName()) : definition.displayName();
    }

    public FilterSelection selectedSelection() {
        return controller.selectedSelection();
    }

    public List<FilterDefinition> filters() {
        return FilterRegistry.get().definitions();
    }

    public List<FilterDefinition> visibleFilters() {
        return FilterRegistry.get().definitions().stream()
                .filter(definition -> PrismodClientConfig.isFilterVisible(definition.key()))
                .toList();
    }

    public void resetSession() {
        controller.resetSession();
        notifyListeners();
    }

    public void setRenderAvailable(boolean available) {
        controller.setRenderAvailable(available);
        notifyListeners();
    }

    public void reportRenderFailure() {
        controller.setRenderAvailable(false);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.prismod.render_failed"), true);
        }
    }

    public void reportFilterFailure(FilterKey key) {
        reportFilterFailure(key, new IllegalStateException("shader load or render failure"));
    }

    public void reportFilterFailure(FilterKey key, Throwable error) {
        FilterRegistry.get().markFailed(key, error);
        refreshConfig();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.prismod.filter_failed",
                    key.serializedName()), true);
        }
    }

    private void notifyListeners() {
        FilterSnapshot snapshot = snapshot();
        for (FilterStateListener listener : listeners) {
            try { listener.onChanged(snapshot); } catch (Throwable ignored) { }
        }
    }

    private final class OverrideHandle implements FilterOverride {
        private final long id;
        private final String ownerId;
        private final net.minecraft.resources.ResourceLocation filter;
        private final int priority;
        private boolean closed;

        private OverrideHandle(long id, String ownerId, net.minecraft.resources.ResourceLocation filter, int priority) {
            this.id = id; this.ownerId = ownerId; this.filter = filter; this.priority = priority;
        }

        @Override public net.minecraft.resources.ResourceLocation id() { return filter; }
        @Override public String ownerId() { return ownerId; }
        @Override public int priority() { return priority; }
        @Override public FilterOverrideState state() { return closed ? FilterOverrideState.CLOSED : FilterOverrideState.ACTIVE; }
        @Override public boolean isActive() { return !closed; }
        @Override public void close() {
            if (!closed) { closed = true; if (controller.removeOverride(id)) notifyListeners(); }
        }
    }
}
