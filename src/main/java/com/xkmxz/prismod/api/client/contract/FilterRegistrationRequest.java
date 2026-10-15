package com.xkmxz.prismod.api.client.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable input for one session-owned custom filter registration. */
public record FilterRegistrationRequest(ResourceLocation logicalId,
                                        ResourceLocation postEffect,
                                        CustomFilterMetadata metadata) {
    public FilterRegistrationRequest {
        Objects.requireNonNull(postEffect, "postEffect");
        metadata = metadata == null ? CustomFilterMetadata.defaults() : metadata;
    }

    public FilterRegistrationRequest(ResourceLocation postEffect, CustomFilterMetadata metadata) {
        this(null, postEffect, metadata);
    }
}
