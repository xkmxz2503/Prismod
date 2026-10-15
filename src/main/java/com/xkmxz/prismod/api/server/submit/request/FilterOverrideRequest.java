package com.xkmxz.prismod.api.server.submit.request;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 可序列化的滤镜覆盖请求。
 *
 * <p>请求由 {@code ownerId} 标识所属覆盖者。服务端保存覆盖时，
 * 同一作用域内再次使用相同 owner 提交请求，表示更新该 owner 的现有策略，
 * 而不是创建无法管理的匿名覆盖。</p>
 *
 * <p>构造时会去除 owner 两端空白、拒绝空 owner，并将强度归一化到
 * {@code 0.0F} 至 {@code 1.0F}；非有限强度按 {@code 0.0F} 处理。</p>
 *
 * @param ownerId 覆盖策略所有者标识，不能为空或全为空白
 * @param filter 要覆盖使用的滤镜资源标识
 * @param strength 覆盖强度，构造后始终位于 {@code [0.0F, 1.0F]}
 * @param priority 覆盖优先级，数值越大表示优先级越高
 */
public record FilterOverrideRequest(String ownerId, ResourceLocation filter, float strength, int priority) {
    /**
     * 创建并归一化一个覆盖请求。
     *
     * @throws IllegalArgumentException 当 {@code ownerId} 为 {@code null}、空字符串或全为空白时抛出
     * @throws NullPointerException 当 {@code filter} 为 {@code null} 时抛出
     */
    public FilterOverrideRequest {
        ownerId = normalizeOwner(ownerId);
        filter = Objects.requireNonNull(filter, "filter");
        strength = normalizeStrength(strength);
    }

    private static String normalizeOwner(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        return value.trim();
    }

    private static float normalizeStrength(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
