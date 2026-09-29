package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState;
import net.minecraft.resources.ResourceLocation;

public interface FilterOverride extends AutoCloseable {
    ResourceLocation id();
    String ownerId();
    int priority();
    FilterOverrideState state();
    boolean isActive();

    @Override
    void close();
}
