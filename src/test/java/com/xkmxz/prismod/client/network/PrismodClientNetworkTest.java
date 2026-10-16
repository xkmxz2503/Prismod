package com.xkmxz.prismod.client.network;

import com.xkmxz.prismod.api.client.submit.result.FilterReceipt;
import com.xkmxz.prismod.api.client.submit.request.FilterRequest;
import com.xkmxz.prismod.api.common.submit.result.FeedbackCode;
import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
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
    void samePolicyWithNewRequestReplacesParametersAndReleasesOldResource() {
        Harness harness = new Harness();
        UUID policyId = UUID.randomUUID();
        UUID oldResource = UUID.randomUUID();
        ResourceLocation nextFilter = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        harness.network.accept(PolicyMessage.override(UUID.randomUUID(), policyId, "server-rule", FILTER, 0.6F, 100));
        harness.creations.get(0).complete(success(oldResource));
        PolicyMessage update = PolicyMessage.override(UUID.randomUUID(), policyId, "server-rule", nextFilter, 0.9F, 200);
        harness.network.accept(update);
        harness.network.accept(update);

        assertEquals(List.of(oldResource), harness.released());
        assertEquals(2, harness.creations.size());
        FilterRequest.Override replacement = harness.requests.stream().filter(FilterRequest.Override.class::isInstance)
                .map(FilterRequest.Override.class::cast).toList().get(1);
        assertEquals(nextFilter, replacement.filter());
        assertEquals(90F, replacement.strength());
        assertEquals(200, replacement.priority());
    }

    @Test
    void pendingUpdateAndClearReleaseBothGenerationsWithoutMixingTheirResources() {
        Harness harness = new Harness();
        UUID policyId = UUID.randomUUID();
        UUID oldResource = UUID.randomUUID();
        UUID newResource = UUID.randomUUID();
        harness.network.accept(PolicyMessage.override(UUID.randomUUID(), policyId, "server-rule", FILTER, 0.6F, 100));
        harness.network.accept(PolicyMessage.override(UUID.randomUUID(), policyId, "server-rule", FILTER, 0.8F, 200));
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), policyId, "server-rule"));
        harness.creations.get(1).complete(success(newResource));
        harness.creations.get(0).complete(success(oldResource));
        assertEquals(List.of(newResource, oldResource), harness.released());
    }

    @Test
    void differentPoliciesWithSameOwnerAndRequestStayIndependent() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        UUID firstPolicy = UUID.randomUUID();
        UUID secondPolicy = UUID.randomUUID();
        UUID firstResource = UUID.randomUUID();
        UUID secondResource = UUID.randomUUID();
        harness.network.accept(PolicyMessage.override(requestId, firstPolicy, "server-rule", FILTER, 0.6F, 100));
        harness.network.accept(PolicyMessage.override(requestId, secondPolicy, "server-rule", FILTER, 0.7F, 200));
        harness.creations.get(0).complete(success(firstResource));
        harness.creations.get(1).complete(success(secondResource));
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), firstPolicy, "server-rule"));
        assertEquals(List.of(firstResource), harness.released());
        harness.network.accept(PolicyMessage.clearAllOverrides(UUID.randomUUID()));
        assertEquals(List.of(firstResource, secondResource), harness.released());
        assertFalse(harness.requests.stream().anyMatch(FilterRequest.ClearOverrides.class::isInstance));
    }

    @Test
    void oldConnectionCompletionCannotReplaceNewConnectionMapping() {
        Harness harness = new Harness();
        UUID policyId = UUID.randomUUID();
        UUID oldResource = UUID.randomUUID();
        UUID newResource = UUID.randomUUID();
        harness.network.accept(PolicyMessage.override(UUID.randomUUID(), policyId, "server-rule", FILTER, 0.6F, 100));
        harness.network.reset();
        PolicyMessage current = PolicyMessage.override(UUID.randomUUID(), policyId, "server-rule", FILTER, 0.8F, 200);
        harness.network.accept(current);
        harness.creations.get(1).complete(success(newResource));
        harness.creations.get(0).complete(success(oldResource));
        harness.network.accept(current);
        assertEquals(2, harness.creations.size());
        assertEquals(List.of(oldResource), harness.released());
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), policyId, "server-rule"));
        assertEquals(List.of(oldResource, newResource), harness.released());
    }

    @Test
    void translatesSelectionAndClearWithoutChangingProtocolValues() {
        Harness harness = new Harness();
        harness.network.accept(PolicyMessage.selection(UUID.randomUUID(), UUID.randomUUID(), FILTER, 0.75F));
        harness.network.accept(PolicyMessage.clearSelection(UUID.randomUUID()));

        FilterRequest.Select select = assertInstanceOf(FilterRequest.Select.class, harness.requests.get(0));
        assertEquals(FILTER, select.filter());
        assertEquals(75F, select.strength());
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
        assertEquals(60F, request.strength());
        assertEquals(100, request.priority());
    }

    @Test
    void clearPendingOverrideReleasesOnlyItsReturnedResource() {
        Harness harness = new Harness();
        UUID requestId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        harness.network.accept(override(requestId));
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), requestId, "server-rule"));
        assertTrue(harness.released().isEmpty());

        harness.creations.get(0).complete(success(resourceId));
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), requestId, "server-rule"));

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
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), requestId, "server-rule"));
        harness.network.accept(override(requestId));
        harness.creations.get(1).complete(success(newResource));
        harness.creations.get(0).complete(success(oldResource));
        assertEquals(List.of(oldResource), harness.released());

        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), requestId, "server-rule"));
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
        harness.network.accept(PolicyMessage.clearOverride(UUID.randomUUID(), requestId, "server-rule"));
        harness.creations.get(0).complete(new FilterReceipt(UUID.randomUUID(), FilterOperationStatus.FAILED,
                FeedbackCode.FAILED, FilterFailureReason.RESOURCE_MISSING, "missing", 0, List.of(), List.of()));
        assertTrue(harness.released().isEmpty());
    }

    @Test
    void ignoresMissingFilterAndKeepsAdapterInstancesIndependent() {
        Harness first = new Harness();
        Harness second = new Harness();
        UUID requestId = UUID.randomUUID();
        first.network.accept(PolicyMessage.selection(requestId, UUID.randomUUID(), null, 1.0F));
        first.network.accept(PolicyMessage.override(requestId, requestId, "server-rule", null, 1.0F, 100));
        assertTrue(first.requests.isEmpty());
        first.network.accept(override(requestId));
        second.network.accept(override(requestId));
        assertEquals(1, first.creations.size());
        assertEquals(1, second.creations.size());
    }

    private static PolicyMessage override(UUID requestId) {
        return PolicyMessage.override(requestId, requestId, "server-rule", FILTER, 0.6F, 100);
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
