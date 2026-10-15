package com.xkmxz.prismod.client.application;

import com.xkmxz.prismod.api.client.submit.model.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.submit.event.FilterEvent;
import com.xkmxz.prismod.api.common.query.view.FilterDescriptor;
import com.xkmxz.prismod.client.application.handle.FilterOverride;
import com.xkmxz.prismod.client.application.handle.FilterRegistration;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.function.Consumer;

/** 应用层内部适配边界；写操作只在客户端线程调用。 */
interface FilterBackend {
    FilterSnapshot snapshot();
    List<FilterDescriptor> filters();
    void observe(Consumer<FilterEvent> listener);
    FilterRegistration register(String owner, ResourceLocation logicalId,
                                ResourceLocation postEffect, CustomFilterMetadata metadata);
    int clearRegistrations(String owner);
    FilterOverride override(String owner, ResourceLocation filter, float strength, int priority);
    int clearOverrides(String owner);
    void select(ResourceLocation filter, float strength);
    void clearSelection();
    void force(ResourceLocation filter, float strength);
    void clearForced();
}
