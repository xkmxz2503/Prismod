package com.xkmxz.prismod.client.network;

import com.xkmxz.prismod.api.client.FilterApi;
import com.xkmxz.prismod.api.client.FilterOverride;
import com.xkmxz.prismod.server.network.FilterCommandPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side receiver for server filter policy packets. */
public final class PrismodClientNetwork {
    private static final Map<UUID, FilterOverride> OVERRIDES = new HashMap<>();

    private PrismodClientNetwork() {
    }

    public static void handle(FilterCommandPacket packet) {
        switch (packet.operation()) {
            case SELECT -> {
                ResourceLocation filter = packet.filter();
                if (filter != null) FilterApi.selectFilter(filter, packet.strength());
            }
            case OVERRIDE -> {
                FilterOverride previous = OVERRIDES.remove(packet.requestId());
                if (previous != null) previous.close();
                if (packet.filter() != null) {
                    OVERRIDES.put(packet.requestId(), FilterApi.createOverride(
                            packet.owner(), packet.filter(), packet.strength(), packet.priority()));
                }
            }
            case CLEAR_OVERRIDE -> {
                FilterOverride previous = OVERRIDES.remove(packet.requestId());
                if (previous != null) previous.close();
            }
            case CLEAR_SELECTION -> FilterApi.selectFilter(ResourceLocation.fromNamespaceAndPath("prismod", "original"), 0.0F);
        }
    }

    public static void resetSession() {
        OVERRIDES.values().forEach(FilterOverride::close);
        OVERRIDES.clear();
    }
}
