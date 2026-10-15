package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class FilterOperationTest {
    @Test
    void queuedOperationPublishesItsFinalResult() {
        MutableFilterOperation operation = new MutableFilterOperation(UUID.randomUUID());

        assertEquals(FilterOperationStatus.QUEUED, operation.status());
        assertFalse(operation.completed());

        operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, 3);

        assertEquals(FilterOperationStatus.SUCCESS, operation.status());
        assertEquals(3, operation.affectedCount());
        assertEquals(FeedbackCode.OK, operation.code());
        assertTrue(operation.completed());
    }

    @Test
    void completedOperationCannotBeCompletedAgain() {
        MutableFilterOperation operation = new MutableFilterOperation(UUID.randomUUID());

        operation.complete(FilterOperationStatus.SUCCESS, FeedbackCode.OK, 3);
        operation.complete(FilterOperationStatus.FAILED, FeedbackCode.FAILED, 9);

        assertEquals(FilterOperationStatus.SUCCESS, operation.status());
        assertEquals(FeedbackCode.OK, operation.code());
        assertEquals(3, operation.affectedCount());
    }

    @Test
    void exposesStableFailureDiagnostics() {
        MutableFilterOperation operation = new MutableFilterOperation(UUID.randomUUID());

        operation.complete(FilterOperationStatus.FAILED, FeedbackCode.INVALID_ARGUMENT,
                FilterFailureReason.ID_CONFLICT, "logical filter ID is already registered", 0);

        assertEquals(FilterFailureReason.ID_CONFLICT, operation.failureReason());
        assertEquals("logical filter ID is already registered", operation.failureDetail());
    }

    @Test
    void completedCompatibilityOperationIsShared() {
        assertSame(FilterOperation.completedOperation(), FilterOperation.completedOperation());
    }
}
