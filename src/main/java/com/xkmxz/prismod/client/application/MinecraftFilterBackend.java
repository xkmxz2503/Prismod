package com.xkmxz.prismod.client.application;

import com.xkmxz.prismod.api.client.submit.model.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.submit.event.FilterEvent;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState;
import com.xkmxz.prismod.client.application.handle.FilterOverride;
import com.xkmxz.prismod.client.application.handle.FilterRegistration;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import com.xkmxz.prismod.client.filter.state.FilterManager;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

/** 复用现有注册表与状态管理，不改变滤镜计算或渲染路径。 */
final class MinecraftFilterBackend implements FilterBackend {
    @Override public FilterSnapshot snapshot() { return FilterManager.get().snapshot(); }
    @Override public List<FilterDescriptor> filters() { return FilterRegistry.get().descriptors(); }
    @Override public void observe(Consumer<FilterEvent> listener) {
        FilterManager.get().subscribeEvents(listener::accept);
    }
    @Override public FilterRegistration register(String owner, ResourceLocation logicalId,
                                                 ResourceLocation postEffect, CustomFilterMetadata metadata) {
        FilterRegistration delegate = FilterRegistry.get().register(logicalId, owner, postEffect, metadata);
        FilterManager.get().registryChanged();
        return new FilterRegistration() {
            @Override public ResourceLocation id() { return delegate.id(); }
            @Override public String ownerId() { return delegate.ownerId(); }
            @Override public FilterRegistrationState state() { return delegate.state(); }
            @Override public FilterFailureReason failureReason() { return delegate.failureReason(); }
            @Override public String failureDetail() { return delegate.failureDetail(); }
            @Override public void close() {
                boolean managed = delegate.state() != FilterRegistrationState.CLOSED;
                delegate.close();
                if (managed) FilterManager.get().registryChanged();
            }
        };
    }
    @Override public int clearRegistrations(String owner) {
        int count = FilterRegistry.get().unregisterOwner(owner);
        if (count > 0) FilterManager.get().registryChanged();
        return count;
    }
    @Override public FilterOverride override(String owner, ResourceLocation filter, float strength, int priority) {
        return FilterManager.get().addOverride(owner, new FilterKey(filter), strength, priority);
    }
    @Override public int clearOverrides(String owner) { return FilterManager.get().removeOverridesByOwner(owner); }
    @Override public void select(ResourceLocation filter, float strength) {
        FilterManager.get().selectSession(new FilterKey(filter), strength);
    }
    @Override public void clearSelection() { FilterManager.get().clearSessionSelection(); }
    @Override public void force(ResourceLocation filter, float strength) { FilterManager.get().setForced(filter, strength); }
    @Override public void clearForced() { FilterManager.get().clearForced(); }
}
