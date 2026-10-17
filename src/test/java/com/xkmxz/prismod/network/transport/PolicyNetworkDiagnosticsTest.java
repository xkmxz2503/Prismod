package com.xkmxz.prismod.network.transport;

import com.xkmxz.prismod.network.contract.PolicyMessage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PolicyNetworkDiagnosticsTest {
    @AfterEach
    void resetReporter() {
        PolicyTransportErrorHandler.resetFailureReporterForTests();
    }

    @Test
    void reportsBroadcastFailureWithMessageAndException() {
        PolicyMessage message = PolicyMessage.selection(UUID.randomUUID(), UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("prismod", "original"), 100.0F);
        RuntimeException failure = new IllegalStateException("send failed");
        AtomicReference<PolicyTransportErrorHandler.FailureContext> observed = new AtomicReference<>();
        PolicyTransportErrorHandler.setFailureReporterForTests(observed::set);

        PolicyNetwork.deliverForTests(PolicyTransportErrorHandler.Direction.BROADCAST, null, message, () -> {
            throw failure;
        });

        PolicyTransportErrorHandler.FailureContext report = observed.get();
        assertEquals(PolicyTransportErrorHandler.Direction.BROADCAST, report.direction());
        assertEquals(message, report.message());
        assertSame(failure, report.error());
    }

    @Test
    void reportsPlayerFailureWithTarget() {
        UUID player = UUID.randomUUID();
        PolicyMessage message = PolicyMessage.clearSelection(UUID.randomUUID());
        RuntimeException failure = new IllegalStateException("send failed");
        AtomicReference<PolicyTransportErrorHandler.FailureContext> observed = new AtomicReference<>();
        PolicyTransportErrorHandler.setFailureReporterForTests(observed::set);

        PolicyNetwork.deliverForTests(PolicyTransportErrorHandler.Direction.PLAYER, player, message, () -> {
            throw failure;
        });

        PolicyTransportErrorHandler.FailureContext report = observed.get();
        assertEquals(PolicyTransportErrorHandler.Direction.PLAYER, report.direction());
        assertEquals(player, report.player());
        assertEquals(message, report.message());
        assertSame(failure, report.error());
    }

    @Test
    void reportsNullMessageWithoutThrowingDuringDiagnostics() {
        RuntimeException failure = new IllegalStateException("send failed");
        AtomicReference<PolicyTransportErrorHandler.FailureContext> observed = new AtomicReference<>();
        PolicyTransportErrorHandler.setFailureReporterForTests(observed::set);

        PolicyNetwork.deliverForTests(PolicyTransportErrorHandler.Direction.BROADCAST, null, null, () -> {
            throw failure;
        });

        assertSame(failure, observed.get().error());
        assertNull(observed.get().message());
    }
}
