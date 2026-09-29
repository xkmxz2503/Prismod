package com.xkmxz.prismod.api.server;

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
        assertEquals(FilterServerApi.NETWORK_CHANNEL_ID, status.networkChannel());
        assertEquals(FilterServerApi.PROTOCOL_VERSION, status.protocolVersion());
    }
}
