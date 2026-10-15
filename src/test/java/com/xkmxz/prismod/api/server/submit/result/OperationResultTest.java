package com.xkmxz.prismod.api.server.submit.result;

import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.submit.result.FeedbackCode;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationResultTest {
    @Test void copiesPolicyIdsAndRejectsQueuedFinalReceipt() {
        List<UUID> policies = new ArrayList<>(List.of(UUID.randomUUID()));
        var receipt = result(FilterOperationStatus.SUCCESS, policies);
        policies.clear();
        assertEquals(1, receipt.policyIds().size());
        assertThrows(UnsupportedOperationException.class, () -> receipt.policyIds().clear());
        assertTrue(receipt.accepted());
        assertThrows(IllegalArgumentException.class, () -> result(FilterOperationStatus.QUEUED, List.of()));
    }
    private OperationResult result(FilterOperationStatus status, List<UUID> policies) {
        return new OperationResult(UUID.randomUUID(), status, FeedbackCode.OK, FilterFailureReason.NONE,
                "", "broadcast", 1, policies, 1);
    }
}
