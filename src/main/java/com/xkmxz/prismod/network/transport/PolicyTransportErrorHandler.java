package com.xkmxz.prismod.network.transport;

import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Centralizes diagnostics for policy transport delivery failures. */
final class PolicyTransportErrorHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Consumer<FailureContext> failureReporter = PolicyTransportErrorHandler::logFailure;

    private PolicyTransportErrorHandler() {
    }

    static void handle(Direction direction, UUID player, PolicyMessage message, RuntimeException error) {
        failureReporter.accept(new FailureContext(direction, player, message, error));
    }

    static void setFailureReporterForTests(Consumer<FailureContext> reporter) {
        failureReporter = Objects.requireNonNull(reporter, "reporter");
    }

    static void resetFailureReporterForTests() {
        failureReporter = PolicyTransportErrorHandler::logFailure;
    }

    private static void logFailure(FailureContext failure) {
        PolicyMessage message = failure.message();
        LOGGER.warn("Prismod policy {} delivery failed channel={} operation={} requestId={} policyId={} player={}",
                failure.direction().name().toLowerCase(), PolicyNetwork.CHANNEL_ID,
                message == null ? null : message.operation(),
                message == null ? null : message.requestId(),
                message == null ? null : message.policyId(),
                failure.player(), failure.error());
    }

    enum Direction { BROADCAST, PLAYER }

    record FailureContext(Direction direction, UUID player, PolicyMessage message, RuntimeException error) { }
}
