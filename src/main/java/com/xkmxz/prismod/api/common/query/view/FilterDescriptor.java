package com.xkmxz.prismod.api.common.query.view;

import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.query.state.filter.FilterType;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 已发现或通过 API 注册的滤镜的不可变公开描述。
 *
 * <p>{@code id} 是逻辑滤镜身份，供选择请求和状态快照引用；它不等同于某个
 * PostChain 文件路径。{@code available} 表示当前资源是否可用；不可用时可通过
 * {@code failureReason} 与 {@code failureDetail} 诊断原因。</p>
 *
 * <p>{@code defaultStrength} 使用客户端百分制，在构造时会归一化到 {@code 0F} 至 {@code 100F}。
 * 缺省的 owner、类型和失败信息会被归一化为各自的空值或默认枚举。</p>
 *
 * @param id 滤镜的逻辑资源标识
 * @param ownerId 注册者或资源所有者标识
 * @param type 滤镜来源或类型
 * @param translationKey 用于显示名称的语言键
 * @param defaultStrength 默认强度
 * @param available 当前是否可以使用该滤镜
 * @param failureReason 不可用时的结构化失败原因
 * @param failureDetail 面向诊断的附加失败信息
 */
public record FilterDescriptor(
        ResourceLocation id,
        String ownerId,
        FilterType type,
        String translationKey,
        float defaultStrength,
        boolean available,
        FilterFailureReason failureReason,
        String failureDetail
) {
    /**
     * 创建并归一化一个滤镜描述。
     *
     * @throws NullPointerException 当 {@code id} 为 {@code null} 时抛出
     */
    public FilterDescriptor {
        id = Objects.requireNonNull(id, "id");
        ownerId = ownerId == null ? "" : ownerId;
        type = type == null ? FilterType.UNKNOWN : type;
        defaultStrength = normalizeStrength(defaultStrength);
        failureReason = failureReason == null ? FilterFailureReason.NONE : failureReason;
        failureDetail = failureDetail == null ? "" : failureDetail;
    }

    private static float normalizeStrength(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(100.0F, value));
    }
}
