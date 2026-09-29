package com.xkmxz.prismod.client.network;

import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client adapter for the frozen policy transport contract. */
public final class PrismodClientNetwork {
    private static final Map<UUID, FilterOverride> OVERRIDES = new HashMap<>();

    private PrismodClientNetwork() {
    }

    public static synchronized void handle(PolicyMessage message) {
        switch (message.operation()) {
            case SELECT -> {
                ResourceLocation filter = message.filter();
                if (filter != null) FilterClientApi.setSessionSelection(filter, message.strength());
            }
            case OVERRIDE -> {
                if (OVERRIDES.containsKey(message.requestId())) return;
                if (message.filter() != null) {
                    OVERRIDES.put(message.requestId(), FilterClientApi.createOverride(
                            message.owner(), message.filter(), message.strength(), message.priority()));
                }
            }
            case CLEAR_OVERRIDE -> {
                FilterOverride previous = OVERRIDES.remove(message.requestId());
                if (previous != null) previous.close();
            }
            case CLEAR_SELECTION -> FilterClientApi.clearSessionSelection();
            case CLEAR_ALL_OVERRIDES -> {
                OVERRIDES.values().forEach(FilterOverride::close);
                OVERRIDES.clear();
            }
        }
    }

    public static synchronized void resetSession() {
        OVERRIDES.values().forEach(FilterOverride::close);
        OVERRIDES.clear();
        FilterClientApi.clearSessionSelection();
    }
}
