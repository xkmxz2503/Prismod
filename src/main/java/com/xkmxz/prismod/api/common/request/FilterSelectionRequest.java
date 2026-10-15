package com.xkmxz.prismod.api.common.request;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 可序列化的滤镜选择请求。
 *
 * <p>该模型由客户端、服务端、网络和命令适配器共享。构造时会校验滤镜标识，
 * 并将强度归一化到 {@code 0.0F} 至 {@code 1.0F}；非有限值会按 {@code 0.0F}
 * 处理。</p>
 *
 * @param filter 要选择的滤镜资源标识
 * @param strength 滤镜强度，构造后始终位于 {@code [0.0F, 1.0F]}
 */
public record FilterSelectionRequest(ResourceLocation filter, float strength) {
    /**
     * 创建并归一化一个滤镜选择请求。
     *
     * @throws NullPointerException 当 {@code filter} 为 {@code null} 时抛出
     */
    public FilterSelectionRequest {
        filter = Objects.requireNonNull(filter, "filter");
        if (!Float.isFinite(strength)) strength = 0.0F;
        strength = Math.max(0.0F, Math.min(1.0F, strength));
    }
}
