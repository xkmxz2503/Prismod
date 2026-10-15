package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.network.contract.PolicyTransport;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FilterServerApiTest {
    @Test
    void exposesStableServerFrameworkStatus() {
        ServerStatus status = FilterServerApi.status();

        assertNotNull(status);
        assertFalse(status.initialized());
        assertEquals(ResourceLocation.fromNamespaceAndPath(
                PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH),
                FilterServerApi.NETWORK_CHANNEL_ID);
        assertEquals(PolicyTransport.PROTOCOL_VERSION, FilterServerApi.PROTOCOL_VERSION);
        assertEquals(FilterServerApi.NETWORK_CHANNEL_ID, status.networkChannel());
        assertEquals(FilterServerApi.PROTOCOL_VERSION, status.protocolVersion());
    }
}
