package com.xkmxz.prismod.api.server;

import net.minecraft.resources.ResourceLocation;

/** Immutable snapshot of the common/server Prismod framework. */
public record ServerStatus(boolean initialized, ResourceLocation networkChannel, String protocolVersion) {
}
