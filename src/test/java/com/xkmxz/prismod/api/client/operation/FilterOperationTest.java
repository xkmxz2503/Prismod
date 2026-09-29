package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
