package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.FilterReceipt;
import com.xkmxz.prismod.api.client.request.FilterRequest;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FilterBatchContractTest {
    @Test
    void receiptDefensivelyCopiesAllLists() {
        List<UUID> ids = new ArrayList<>(List.of(UUID.randomUUID()));
        List<FilterReceipt> items = new ArrayList<>();
        FilterReceipt receipt = new FilterReceipt(UUID.randomUUID(), FilterOperationStatus.SUCCESS,
                FeedbackCode.OK, FilterFailureReason.NONE, "", 1, ids, items);
        ids.clear();
        items.add(receipt);
        assertEquals(1, receipt.resourceIds().size());
        assertTrue(receipt.items().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> receipt.resourceIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> receipt.items().add(receipt));
        assertTrue(Arrays.stream(FilterReceipt.class.getDeclaredMethods())
                .noneMatch(method -> List.of("close", "state", "isActive", "operation").contains(method.getName())));
    }

    @Test
    void finalReceiptCannotContainQueuedStatus() {
        assertThrows(IllegalArgumentException.class, () -> new FilterReceipt(UUID.randomUUID(),
                FilterOperationStatus.QUEUED, FeedbackCode.OK, FilterFailureReason.NONE, "",
                0, List.of(), List.of()));
    }

    @Test
    void batchCopiesInputsButPreservesInvalidItemsForPerItemFeedback() {
        List<FilterRequest> requests = new ArrayList<>();
        requests.add(null);
        FilterRequest.Batch batch = new FilterRequest.Batch(UUID.randomUUID(), requests);
        requests.clear();
        assertEquals(1, batch.requests().size());
        assertNull(batch.requests().get(0));
        assertThrows(UnsupportedOperationException.class, () -> batch.requests().clear());
    }
}
