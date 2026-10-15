package com.xkmxz.prismod.client.network;

import com.xkmxz.prismod.api.client.contract.FilterReceipt;
import com.xkmxz.prismod.api.client.request.FilterRequest;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PrismodClientNetworkTest {
    private static final ResourceLocation FILTER = ResourceLocation.fromNamespaceAndPath("prismod", "vintage");

    @Test
    void translatesSelectionAndClearWithoutChangingProtocolValues() {
        Harness harness = new Harness();
        harness.network.accept(PolicyMessage.selection(UUID.randomUUID(), FILTER, 0.75F));
        harness.network.accept(PolicyMessage.clearSelection(UUID.randomUUID()));

        FilterRequest.Select select = assertInstanceOf(FilterRequest.Select.class, harness.requests.get(0));
        assertEquals(FILTER, select.filter());
        assertEquals(0.75F, select.strength());
        assertInstanceOf(FilterRequest.ClearSelection.class, harness.requests.get(1));
    }

    @Test
    void deduplicatesOverrideBeforeAndAfterCreationCompletes() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        PolicyMessage message = override(requestId);
        harness.network.accept(message);
        harness.network.accept(message);
        harness.creations.get(0).complete(success(UUID.randomUUID()));
        harness.network.accept(message);

        assertEquals(1, harness.creations.size());
        FilterRequest.Override request = assertInstanceOf(FilterRequest.Override.class, harness.requests.get(0));
        assertEquals("server-rule", request.ownerId());
        assertEquals(FILTER, request.filter());
        assertEquals(0.6F, request.strength());
        assertEquals(100, request.priority());
    }

    @Test
    void clearPendingOverrideReleasesOnlyItsReturnedResource() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        harness.network.accept(override(requestId));
        harness.network.accept(PolicyMessage.clearOverride(requestId, "server-rule"));
        assertTrue(harness.released().isEmpty());

        harness.creations.get(0).complete(success(resourceId));
        harness.network.accept(PolicyMessage.clearOverride(requestId, "server-rule"));

        assertEquals(List.of(resourceId), harness.released());
    }

    @Test
    void clearAllReleasesCompletedAndPendingCreationsOnce() {
        Harness harness = new Harness();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        harness.network.accept(override(UUID.randomUUID()));
        harness.network.accept(override(UUID.randomUUID()));
        harness.creations.get(0).complete(success(first));
        harness.network.accept(PolicyMessage.clearAllOverrides(UUID.randomUUID()));
        assertEquals(List.of(first), harness.released());

        harness.creations.get(1).complete(success(second));
        harness.network.accept(PolicyMessage.clearAllOverrides(UUID.randomUUID()));
        assertEquals(List.of(first, second), harness.released());
    }

    @Test
    void resetClearsSelectionAndReleasesPendingCreation() {
        Harness harness = new Harness();
        UUID resourceId = UUID.randomUUID();
        harness.network.accept(override(UUID.randomUUID()));
        harness.network.reset();
        assertInstanceOf(FilterRequest.ClearSelection.class, harness.requests.get(1));
        assertTrue(harness.released().isEmpty());

        harness.creations.get(0).complete(success(resourceId));
        assertEquals(List.of(resourceId), harness.released());
    }

    @Test
    void reusedRequestIdDoesNotLetOldReceiptReleaseNewResource() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        UUID oldResource = UUID.randomUUID();
        UUID newResource = UUID.randomUUID();
        harness.network.accept(override(requestId));
        harness.network.accept(PolicyMessage.clearOverride(requestId, "server-rule"));
        harness.network.accept(override(requestId));
        harness.creations.get(1).complete(success(newResource));
        harness.creations.get(0).complete(success(oldResource));
        assertEquals(List.of(oldResource), harness.released());

        harness.network.accept(PolicyMessage.clearOverride(requestId, "server-rule"));
        assertEquals(List.of(oldResource, newResource), harness.released());
    }

    @Test
    void resetAllowsRequestIdReuseWithoutOldReceiptClearingNewEntry() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        UUID oldResource = UUID.randomUUID();
        UUID newResource = UUID.randomUUID();
        harness.network.accept(override(requestId));
        harness.network.reset();
        harness.network.accept(override(requestId));
        harness.creations.get(0).complete(success(oldResource));
        harness.creations.get(1).complete(success(newResource));
        harness.network.accept(override(requestId));
        assertEquals(2, harness.creations.size());
        assertEquals(List.of(oldResource), harness.released());

        harness.network.reset();
        assertEquals(List.of(oldResource, newResource), harness.released());
    }

    @Test
    void failedCreationProducesNoReleaseRequest() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        harness.network.accept(override(requestId));
        harness.network.accept(PolicyMessage.clearOverride(requestId, "server-rule"));
        harness.creations.get(0).complete(new FilterReceipt(UUID.randomUUID(), FilterOperationStatus.FAILED,
                FeedbackCode.FAILED, FilterFailureReason.RESOURCE_MISSING, "missing", 0, List.of(), List.of()));
        assertTrue(harness.released().isEmpty());
    }

    @Test
    void ignoresMissingFilterAndKeepsAdapterInstancesIndependent() {
        Harness first = new Harness();
        Harness second = new Harness();
        UUID requestId = UUID.randomUUID();
        first.network.accept(PolicyMessage.selection(requestId, null, 1.0F));
        first.network.accept(PolicyMessage.override(requestId, "server-rule", null, 1.0F, 100));
        assertTrue(first.requests.isEmpty());
        first.network.accept(override(requestId));
        second.network.accept(override(requestId));
        assertEquals(1, first.creations.size());
        assertEquals(1, second.creations.size());
    }

    private static PolicyMessage override(UUID requestId) {
        return PolicyMessage.override(requestId, "server-rule", FILTER, 0.6F, 100);
    }

    private static FilterReceipt success(UUID resourceId) {
        return new FilterReceipt(UUID.randomUUID(), FilterOperationStatus.SUCCESS, FeedbackCode.OK,
                FilterFailureReason.NONE, "", 1, List.of(resourceId), List.of());
    }

    private static final class Harness implements Function<FilterRequest, CompletionStage<FilterReceipt>> {
        final List<FilterRequest> requests = new ArrayList<>();
        final List<CompletableFuture<FilterReceipt>> creations = new ArrayList<>();
        final PrismodClientNetwork network = new PrismodClientNetwork(this);

        @Override
        public CompletionStage<FilterReceipt> apply(FilterRequest request) {
            requests.add(request);
            if (request instanceof FilterRequest.Override) {
                CompletableFuture<FilterReceipt> future = new CompletableFuture<>();
                creations.add(future);
                return future;
            }
            return CompletableFuture.completedFuture(new FilterReceipt(UUID.randomUUID(),
                    FilterOperationStatus.SUCCESS, FeedbackCode.OK, FilterFailureReason.NONE,
                    "", 0, List.of(), List.of()));
        }

        List<UUID> released() {
            return requests.stream().filter(FilterRequest.Release.class::isInstance)
                    .map(FilterRequest.Release.class::cast).map(FilterRequest.Release::resourceId).toList();
        }
    }
}
