package com.xkmxz.prismod.api.common.query.view;

import com.xkmxz.prismod.api.common.query.state.filter.FilterFallbackReason;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 可安全保存或跨线程传递的不可变滤镜状态快照。
 *
 * <p>{@code filter}/{@code strength} 表示当前最终生效的滤镜和强度；
 * {@code selectedFilter}/{@code selectedStrength} 表示用户或策略选择的目标。
 * 两者可能不同，例如资源不可用发生回退，或当前存在更高优先级覆盖。</p>
 *
 * <p>{@code overrideOwner} 和 {@code overridePriority} 描述当前生效的覆盖来源；
 * 没有覆盖时 owner 为空。{@code renderAvailable} 与 {@code fallbackReason} 用于
 * 解释渲染资源是否可用，{@code generation} 用于比较快照的新旧关系。两个强度值
 * 都会在构造时归一化到 {@code 0.0F} 至 {@code 1.0F}。</p>
 *
 * @param filter 当前最终生效的滤镜
 * @param strength 当前最终生效的强度
 * @param selectedFilter 用户或策略选择的滤镜
 * @param selectedStrength 用户或策略选择的强度
 * @param overrideOwner 当前生效覆盖的 owner；无覆盖时为空
 * @param overridePriority 当前生效覆盖的优先级
 * @param renderAvailable 当前滤镜资源是否可用于渲染
 * @param fallbackReason 当前是否发生回退及其原因
 * @param generation 对应的状态代数
 */
public record FilterSnapshot(
        ResourceLocation filter,
        float strength,
        ResourceLocation selectedFilter,
        float selectedStrength,
        String overrideOwner,
        int overridePriority,
        boolean renderAvailable,
        FilterFallbackReason fallbackReason,
        long generation
) {
    /**
     * 创建并归一化一个滤镜状态快照。
     *
     * @throws NullPointerException 当最终滤镜或选择滤镜为 {@code null} 时抛出
     */
    public FilterSnapshot {
        filter = Objects.requireNonNull(filter, "filter");
        selectedFilter = Objects.requireNonNull(selectedFilter, "selectedFilter");
        strength = normalizeStrength(strength);
        selectedStrength = normalizeStrength(selectedStrength);
        overrideOwner = overrideOwner == null ? "" : overrideOwner;
        fallbackReason = fallbackReason == null ? FilterFallbackReason.NONE : fallbackReason;
    }

    private static float normalizeStrength(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
