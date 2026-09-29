package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.OperationResult;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerNetworkApiTest {
    private static final ResourceLocation FILTER = ResourceLocation.fromNamespaceAndPath("prismod", "original");

    @Test
    void broadcastSelectionCreatesStableRequestResult() {
        OperationResult result = FilterServerApi.broadcastSelection(new FilterSelectionRequest(FILTER, 0.75F));

        assertNotNull(result.requestId());
        assertEquals(FilterOperationStatus.SUCCESS, result.status());
        assertEquals("broadcast", result.target());
        assertEquals(1, result.affectedCount());
    }

    @Test
    void broadcastOverrideCanBeReplacedAndClearedByOwner() {
        FilterOverrideRequest request = new FilterOverrideRequest("server-rule", FILTER, 1.0F, 50);
        OperationResult first = FilterServerApi.broadcastOverride(request);
        OperationResult second = FilterServerApi.broadcastOverride(request);

        assertEquals(first.requestId(), second.requestId());
        assertEquals(FilterOperationStatus.SUCCESS, FilterServerApi.clearBroadcastOverride("server-rule").status());
    }

    @Test
    void clearsEveryServerOverrideAndExposesPolicySummary() {
        FilterServerApi.broadcastOverride(new FilterOverrideRequest("clear-all-test", FILTER, 0.5F, 4));

        assertEquals(1, FilterServerApi.filterStatus().globalOverrideCount());
        assertEquals(FilterOperationStatus.SUCCESS, FilterServerApi.clearAllOverrides().status());
        assertEquals(0, FilterServerApi.filterStatus().globalOverrideCount());
    }
}
