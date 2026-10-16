package com.xkmxz.prismod.network;

import com.xkmxz.prismod.network.contract.PolicyMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PolicyMessageTest {
    @Test
    void rejectsMissingOperationAndRequestIdentityRatherThanChangingMeaning() {
        assertThrows(NullPointerException.class,
                () -> new PolicyMessage(null, UUID.randomUUID(), null, "", null, 0, 0));
        assertThrows(NullPointerException.class, () -> PolicyMessage.clearSelection(null));
    }

    @Test
    void roundTripsEveryOperationIncludingOptionalPolicyIdentity() {
        UUID requestId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        ResourceLocation filter = ResourceLocation.fromNamespaceAndPath("example", "debug");
        for (PolicyMessage source : List.of(
                PolicyMessage.selection(requestId, policyId, filter, 70.0F),
                PolicyMessage.override(requestId, policyId, " rule ", filter, 200.0F, 17),
                PolicyMessage.clearSelection(requestId),
                PolicyMessage.clearOverride(requestId, policyId, "rule"),
                PolicyMessage.clearAllOverrides(requestId))) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PolicyMessage.encode(source, buffer);
                assertEquals(source, PolicyMessage.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void preservesNormalizationAndSeparateIdentities() {
        UUID requestId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        PolicyMessage message = PolicyMessage.override(requestId, policyId, " rule ", null, 200.0F, 17);
        assertEquals("rule", message.owner());
        assertEquals(100.0F, message.strength());
        assertEquals(requestId, message.requestId());
        assertEquals(policyId, message.policyId());
        assertEquals(0.0F, PolicyMessage.selection(requestId, policyId, null, Float.NaN).strength());
        assertEquals(0.0F, PolicyMessage.selection(requestId, policyId, null, -1.0F).strength());
        assertEquals(0.0F, PolicyMessage.selection(requestId, policyId, null, Float.POSITIVE_INFINITY).strength());
    }

    @Test
    void requiresPolicyOnlyForPersistentOrIndividualOperations() {
        UUID requestId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> PolicyMessage.selection(requestId, null, null, 1));
        assertThrows(NullPointerException.class, () -> PolicyMessage.override(requestId, null, "rule", null, 1, 0));
        assertThrows(NullPointerException.class, () -> PolicyMessage.clearOverride(requestId, null, "rule"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyMessage(
                PolicyMessage.Operation.CLEAR_SELECTION, requestId, UUID.randomUUID(), "", null, 0, 0));
        assertNull(PolicyMessage.clearSelection(requestId).policyId());
        assertNull(PolicyMessage.clearAllOverrides(requestId).policyId());
    }

    @Test
    void invalidOperationOrdinalIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(99);
            assertThrows(RuntimeException.class, () -> PolicyMessage.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
