package com.xkmxz.prismod.client.application.handle;

import com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState;
import net.minecraft.resources.ResourceLocation;

/** 状态层内部覆盖句柄，不属于外部模组 API。 */
public interface FilterOverride extends AutoCloseable {
    ResourceLocation id();
    String ownerId();
    int priority();
    FilterOverrideState state();
    boolean isActive();
    @Override void close();
}
