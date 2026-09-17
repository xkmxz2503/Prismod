package com.xkmxz.prismod.client;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;

public final class PrismodClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CYCLE_ORDER;
    private static final ForgeConfigSpec.DoubleValue[] STRENGTHS = new ForgeConfigSpec.DoubleValue[FilterId.values().length];
    private static final List<String> DEFAULT_ORDER = Arrays.stream(FilterId.values()).map(FilterId::serializedName).toList();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.comment("是否启用 Prismod 世界滤镜；其他模组的强制覆盖不受此开关限制。").define("enabled", true);
        // 列表内容由 parseCycleOrder 整体验证，避免 Forge 先移除未知项而误判为有效顺序。
        CYCLE_ORDER = builder.comment("F8 切换顺序，必须恰好包含全部六个不同的滤镜 ID。")
                .define("cycle_order", DEFAULT_ORDER, value -> {
                    if (value instanceof List<?>) return true;
                    LOGGER.warn("Prismod 的 cycle_order 必须是列表，已回退为默认顺序。");
                    return false;
                });
        for (FilterId id : FilterId.values()) {
            STRENGTHS[id.ordinal()] = builder.comment("滤镜强度，范围 0.0 至 1.0。")
                    .defineInRange("strength_" + id.serializedName(), 1.0D, 0.0D, 1.0D);
        }
        SPEC = builder.build();
    }

    private PrismodClientConfig() {
    }

    public static List<FilterId> cycleOrder() {
        return FilterId.parseCycleOrder(CYCLE_ORDER.get(), LOGGER::warn);
    }

    public static void setCycleOrder(List<FilterId> order) {
        Object serialized = order == null ? null : order.stream()
                .map(id -> id == null ? null : id.serializedName()).toList();
        List<FilterId> validOrder = FilterId.parseCycleOrder(serialized, LOGGER::warn);
        CYCLE_ORDER.set(validOrder.stream().map(FilterId::serializedName).toList());
    }

    public static float strength(FilterId id) {
        return FilterState.normalizeStrength(STRENGTHS[id.ordinal()].get());
    }

    public static void setStrength(FilterId id, double value) {
        STRENGTHS[id.ordinal()].set((double) FilterState.normalizeStrength(value));
    }
}
