package com.xkmxz.prismod.server.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilterCommandPacketTest {
    @Test
    void roundTripsOverrideAndClearPackets() {
        UUID requestId = UUID.randomUUID();
        FilterCommandPacket packet = FilterCommandPacket.override(
                requestId, "server-rule", ResourceLocation.fromNamespaceAndPath("prismod", "original"), 0.5F, 9);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        FilterCommandPacket.encode(packet, buffer);
        FilterCommandPacket decoded = FilterCommandPacket.decode(buffer);

        assertEquals(packet, decoded);
        FilterCommandPacket clear = FilterCommandPacket.clearOverride(requestId, "server-rule");
        FriendlyByteBuf clearBuffer = new FriendlyByteBuf(Unpooled.buffer());
        FilterCommandPacket.encode(clear, clearBuffer);
        assertNull(FilterCommandPacket.decode(clearBuffer).filter());
    }
}
