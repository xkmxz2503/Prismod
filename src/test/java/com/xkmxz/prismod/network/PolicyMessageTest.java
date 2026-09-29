package com.xkmxz.prismod.network;

import com.xkmxz.prismod.network.contract.PolicyMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolicyMessageTest {
    @Test
    void roundTripsEveryPayloadField() {
        PolicyMessage source = PolicyMessage.override(UUID.randomUUID(), " rule ",
                ResourceLocation.fromNamespaceAndPath("example", "debug"), 2.0F, 17);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        PolicyMessage.encode(source, buffer);
        PolicyMessage decoded = PolicyMessage.decode(buffer);

        assertEquals("rule", decoded.owner());
        assertEquals(1.0F, decoded.strength());
        assertEquals(source.filter(), decoded.filter());
        assertEquals(source.priority(), decoded.priority());
        assertEquals(source.operation(), decoded.operation());
    }

    @Test
    void clearMessagesHaveNoFilterAndStableOperation() {
        PolicyMessage message = PolicyMessage.clearAllOverrides(UUID.randomUUID());
        assertEquals(PolicyMessage.Operation.CLEAR_ALL_OVERRIDES, message.operation());
        assertNull(message.filter());
    }
}
