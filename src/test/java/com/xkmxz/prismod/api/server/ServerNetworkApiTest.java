package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.api.server.submit.model.ServerFilterTarget;
import com.xkmxz.prismod.api.server.submit.request.ServerFilterRequest;
import com.xkmxz.prismod.api.server.submit.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.server.query.request.ServerFilterQuery;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 真实公开门面在无服务端运行环境时也必须返回终态，不能伪装发送成功。 */
class ServerNetworkApiTest {
    @Test void unavailableServerReturnsDistinctFinalReceiptsWithoutSavingState() {
        var request = new ServerFilterRequest.Select(new ServerFilterTarget.Broadcast(),
                new FilterSelectionRequest(ResourceLocation.fromNamespaceAndPath("prismod", "warm"), 75F));
        var first = FilterServerApi.submit(request).toCompletableFuture().join();
        var second = FilterServerApi.submit(request).toCompletableFuture().join();
        assertEquals(FilterOperationStatus.UNAVAILABLE, first.status());
        assertFalse(first.accepted());
        assertNotEquals(first.requestId(), second.requestId());
        assertTrue(first.policyIds().isEmpty());
        assertNull(FilterServerApi.query(new ServerFilterQuery.FilterStatus()).globalSelection());
    }
}
