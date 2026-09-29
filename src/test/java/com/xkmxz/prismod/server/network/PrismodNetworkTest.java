package com.xkmxz.prismod.server.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismodNetworkTest {
    @Test
    void acceptsMatchingAndOptionalPeerVersions() {
        assertEquals("2", PrismodNetwork.PROTOCOL_VERSION);
        assertTrue(PrismodNetwork.acceptsProtocolVersion(PrismodNetwork.PROTOCOL_VERSION));
        assertTrue(PrismodNetwork.acceptsProtocolVersion(PrismodNetwork.ABSENT_VERSION));
        assertTrue(PrismodNetwork.acceptsProtocolVersion(PrismodNetwork.VANILLA_VERSION));
        assertFalse(PrismodNetwork.acceptsProtocolVersion("0"));
    }

    @Test
    void emptyPacketHasNoPayload() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PrismodNetwork.EmptyPacket.encode(new PrismodNetwork.EmptyPacket(), buffer);

        assertEquals(0, buffer.readableBytes());
        assertNotNull(PrismodNetwork.EmptyPacket.decode(buffer));
    }
}
