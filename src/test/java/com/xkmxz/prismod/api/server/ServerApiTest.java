package com.xkmxz.prismod.api.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerApiTest {
    @Test
    void exposesStableServerFrameworkStatus() {
        ServerStatus status = ServerApi.status();

        assertNotNull(status);
        assertFalse(status.initialized());
        assertEquals(ServerApi.NETWORK_CHANNEL_ID, status.networkChannel());
        assertEquals(ServerApi.PROTOCOL_VERSION, status.protocolVersion());
    }
}
