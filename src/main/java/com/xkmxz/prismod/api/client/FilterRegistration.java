package com.xkmxz.prismod.api.client;

import net.minecraft.resources.ResourceLocation;

/** Handle for one owner-scoped custom filter registration. */
public interface FilterRegistration extends AutoCloseable {
    ResourceLocation id();

    @Override
    void close();
}
