package com.xkmxz.prismod.api.server.query.view;

import net.minecraft.resources.ResourceLocation;

/**
 * 服务端拥有的滤镜策略状态快照。
 *
 * <p>快照同时包含全局策略和玩家级策略的数量，用于诊断服务端当前保存了多少条
 * 策略。{@code globalSelection} 与 {@code globalStrength} 只描述全局选择；玩家级
 * 选择需要通过 {@code playerSelectionCount} 观察数量，具体玩家目标由发送操作决定。</p>
 *
 * <p>{@code generation} 在服务端策略状态发生有效变化时递增，可用于比较两次快照的
 * 新旧关系。强度在构造时会被归一化到 {@code 0.0F} 至 {@code 1.0F}。</p>
 *
 * @param globalSelection 当前全局选择的滤镜，未设置时为 {@code null}
 * @param globalStrength 当前全局选择强度
 * @param globalOverrideCount 全局覆盖策略数量
 * @param playerSelectionCount 存在玩家级选择的玩家数量
 * @param playerOverrideCount 玩家级覆盖策略总数量
 * @param generation 当前服务端策略状态代数
 */
public record ServerFilterStatus(
        ResourceLocation globalSelection,
        float globalStrength,
        int globalOverrideCount,
        int playerSelectionCount,
        int playerOverrideCount,
        long generation
) {
    public ServerFilterStatus {
        globalSelection = globalSelection;
        globalStrength = Float.isFinite(globalStrength) ? Math.max(0.0F, Math.min(1.0F, globalStrength)) : 0.0F;
    }
}
