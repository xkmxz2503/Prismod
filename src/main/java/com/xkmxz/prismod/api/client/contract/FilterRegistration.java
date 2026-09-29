package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState;
import net.minecraft.resources.ResourceLocation;

/** Handle for one owner-scoped custom filter registration. */
public interface FilterRegistration extends AutoCloseable {
    ResourceLocation id();

    String ownerId();

    FilterRegistrationState state();

    default boolean isActive() {
        return state() == FilterRegistrationState.ACTIVE;
    }

    default FilterFailureReason failureReason() {
        return FilterFailureReason.NONE;
    }

    default String failureDetail() {
        return "";
    }

    @Override
    void close();
}
