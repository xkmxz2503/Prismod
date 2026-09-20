package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.config.PrismodClientConfig;
import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 状态读取可跨线程；所有写操作以及配置读取只由客户端线程执行。 */
public final class FilterManager {
    private static final FilterManager INSTANCE = new FilterManager();
    private final FilterController controller = new FilterController();

    private FilterManager() {
    }

    public static FilterManager get() {
        return INSTANCE;
    }

    public FilterState effectiveState() {
        return controller.effectiveState();
    }

    public void cycle() {
        controller.cycle();
    }

    public void select(FilterId id) {
        controller.select(id);
    }

    public void select(FilterKey key) {
        controller.select(key);
    }

    public void setForced(FilterId id, float strength) {
        controller.setForced(id, strength);
    }

    public void setForced(FilterKey key, float strength) {
        controller.setForced(key, strength);
    }

    public void clearForced() {
        controller.clearForced();
    }

    public FilterState selectedState() {
        return controller.selectedState();
    }

    public boolean isForced() {
        return controller.isForced();
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
    }

    public void setRenderAvailable(boolean available) {
        controller.setRenderAvailable(available);
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
}
