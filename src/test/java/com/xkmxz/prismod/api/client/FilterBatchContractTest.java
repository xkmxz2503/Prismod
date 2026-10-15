package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.contract.FilterBatchItem;
import com.xkmxz.prismod.api.client.contract.FilterBatchResult;
import com.xkmxz.prismod.api.client.contract.FilterRegistrationRequest;
import com.xkmxz.prismod.api.client.operation.FilterBatchOperation;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterBatchContractTest {
    @Test
    void registrationRequestNormalizesAndPreservesFields() {
        FilterRegistrationRequest request = new FilterRegistrationRequest(
                ResourceLocation.fromNamespaceAndPath("example", "debug"),
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
                new CustomFilterMetadata("filter.example.debug", 0.75F));

        assertEquals("example:debug", request.logicalId().toString());
        assertEquals("example:shaders/post/debug.json", request.postEffect().toString());
        assertEquals(0.75F, request.metadata().defaultStrength());
    }

    @Test
    void batchResultListsAreImmutable() {
        FilterBatchOperation operation = new FilterBatchOperation(UUID.randomUUID(),
                List.of(), FilterOperationStatus.SUCCESS, FeedbackCode.OK, 0, 0, 0,
                com.xkmxz.prismod.api.common.state.filter.FilterFailureReason.NONE, "");
        FilterBatchResult<Object> result = new FilterBatchResult<>(List.of(), operation,
                List.<FilterBatchItem<Object>>of());

        assertThrows(UnsupportedOperationException.class, () -> result.handles().add(new Object()));
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
        assertEquals(FilterOperationStatus.SUCCESS, result.status());
    }
}
