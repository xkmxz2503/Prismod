package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.common.state.FilterOperationStatus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerNetworkApiTest {
    private static final ResourceLocation FILTER = ResourceLocation.fromNamespaceAndPath("prismod", "original");

    @Test
    void broadcastSelectionCreatesStableRequestResult() {
        ServerOperationResult result = ServerApi.broadcastSelection(new FilterSelectionRequest(FILTER, 0.75F));

        assertNotNull(result.requestId());
        assertEquals(FilterOperationStatus.SUCCESS, result.status());
        assertEquals("broadcast", result.target());
        assertEquals(0.75F, result.strength());
    }

    @Test
    void broadcastOverrideCanBeReplacedAndClearedByOwner() {
        FilterOverrideRequest request = new FilterOverrideRequest("server-rule", FILTER, 1.0F, 50);
        ServerOperationResult first = ServerApi.broadcastOverride(request);
        ServerOperationResult second = ServerApi.broadcastOverride(request);

        assertEquals(first.requestId(), second.requestId());
        assertEquals(FilterOperationStatus.SUCCESS, ServerApi.clearBroadcastOverride("server-rule").status());
    }

    @Test
    void clearsEveryServerOverrideAndExposesPolicySummary() {
        ServerApi.broadcastOverride(new FilterOverrideRequest("clear-all-test", FILTER, 0.5F, 4));

        assertEquals(1, ServerApi.filterStatus().globalOverrideCount());
        assertEquals(FilterOperationStatus.SUCCESS, ServerApi.clearAllOverrides().status());
        assertEquals(0, ServerApi.filterStatus().globalOverrideCount());
    }
}
