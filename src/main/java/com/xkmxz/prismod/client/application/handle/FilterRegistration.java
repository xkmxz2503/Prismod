package com.xkmxz.prismod.client.application.handle;

import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.client.query.state.lifecycle.FilterRegistrationState;
import net.minecraft.resources.ResourceLocation;

/** 注册表内部生命周期句柄，不属于外部模组 API。 */
public interface FilterRegistration extends AutoCloseable {
    ResourceLocation id();
    String ownerId();
    FilterRegistrationState state();
    default boolean isActive() { return state() == FilterRegistrationState.ACTIVE; }
    default FilterFailureReason failureReason() { return FilterFailureReason.NONE; }
    default String failureDetail() { return ""; }
    @Override void close();
}
