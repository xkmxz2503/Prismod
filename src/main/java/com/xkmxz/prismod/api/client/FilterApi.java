package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterManager;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.client.filter.FilterState;
import com.xkmxz.prismod.client.filter.FilterRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

/** 仅供客户端调用；写入会调度至客户端线程，读取返回最近一次已应用的不可变快照。 */
public final class FilterApi {
    private FilterApi() {
    }

    /** 设置单一强制覆盖；强度超界会截断，NaN 与无穷值按零处理。 */
    public static void setActiveFilter(FilterId id, float strength) {
        runOnClientThread(() -> FilterManager.get().setForced(id, strength));
    }

    public static FilterRegistration registerCustomFilter(String ownerId, ResourceLocation postEffect,
                                                           CustomFilterMetadata metadata) {
        return FilterRegistry.get().register(ownerId, postEffect, metadata);
    }

    public static void setActiveFilter(ResourceLocation id, float strength) {
        runOnClientThread(() -> FilterManager.get().setForced(
                FilterKey.fromPostEffect(id), strength));
    }

    /** 清除强制覆盖，并恢复当前用户选择、总开关及配置强度。 */
    public static void clearForcedFilter() {
        runOnClientThread(FilterManager.get()::clearForced);
    }

    /** 无需等待客户端线程；尚未执行的排队写操作不包含在此快照中。 */
    public static FilterState getEffectiveState() {
        return FilterManager.get().effectiveState();
    }

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) action.run();
        else minecraft.execute(action);
    }
}
