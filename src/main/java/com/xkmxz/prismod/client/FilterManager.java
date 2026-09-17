package com.xkmxz.prismod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;

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

    public void setForced(FilterId id, float strength) {
        controller.setForced(id, strength);
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
        EnumMap<FilterId, Float> strengths = new EnumMap<>(FilterId.class);
        for (FilterId id : FilterId.values()) strengths.put(id, PrismodClientConfig.strength(id));
        controller.refreshConfig(PrismodClientConfig.ENABLED.get(), PrismodClientConfig.cycleOrder(), strengths);
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
}
