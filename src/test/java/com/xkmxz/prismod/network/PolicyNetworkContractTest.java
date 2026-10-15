package com.xkmxz.prismod.network;

import com.xkmxz.prismod.network.contract.PolicyTransport;
import com.xkmxz.prismod.network.transport.PolicyNetwork;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PolicyNetworkContractTest {
    @Test
    void exposesOnlyTheNewStrictProtocol() {
        assertEquals("prismod:policy", PolicyNetwork.CHANNEL_ID.toString());
        assertEquals("1", PolicyNetwork.PROTOCOL_VERSION);
        assertFalse(PolicyNetwork.PROTOCOL_VERSION.equals("2"));
    }

    @Test
    void transportPortContainsOnlyServerSendOperations() {
        Set<String> methods = Arrays.stream(PolicyTransport.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("broadcast", "send"), methods);
    }
}
