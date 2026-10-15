package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.FilterSnapshot;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterSnapshotTest {
    @Test
    void legacyHandlesExposeCompletedCompatibilityOperations() {
        FilterRegistration registration = new FilterRegistration() {
            @Override public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("example", "test"); }
            @Override public String ownerId() { return "owner"; }
            @Override public FilterRegistrationState state() { return FilterRegistrationState.ACTIVE; }
            @Override public void close() { }
        };
        FilterOverride override = new FilterOverride() {
            @Override public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("example", "test"); }
            @Override public String ownerId() { return "owner"; }
            @Override public int priority() { return 1; }
            @Override public FilterOverrideState state() { return FilterOverrideState.ACTIVE; }
            @Override public boolean isActive() { return true; }
            @Override public void close() { }
        };

        assertTrue(registration.operation().completed());
        assertEquals(com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus.SUCCESS,
                registration.operation().status());
        assertSame(registration.operation(), registration.operation());
        assertTrue(override.operation().completed());
        assertSame(override.operation(), override.operation());
    }

    @Test
    void preservesCompleteCustomFilterIdentity() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");
        com.xkmxz.prismod.api.client.contract.FilterSnapshot snapshot =
                new com.xkmxz.prismod.api.client.contract.FilterSnapshot(id, 0.75F, true, true);
        assertEquals(id, snapshot.filter());
        assertEquals("example:debug", snapshot.filter().toString());
        assertEquals(0.75F, snapshot.strength());
        assertTrue(snapshot.forced());
        assertTrue(snapshot.renderAvailable());
    }

    @Test
    void normalizesStrengthAndKeepsSnapshotImmutable() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        com.xkmxz.prismod.api.client.contract.FilterSnapshot low =
                new com.xkmxz.prismod.api.client.contract.FilterSnapshot(id, -1.0F, false, true);
        com.xkmxz.prismod.api.client.contract.FilterSnapshot high =
                new com.xkmxz.prismod.api.client.contract.FilterSnapshot(id, 2.0F, false, true);
        com.xkmxz.prismod.api.client.contract.FilterSnapshot invalid =
                new com.xkmxz.prismod.api.client.contract.FilterSnapshot(id, Float.NaN, false, false);
        assertEquals(0.0F, low.strength());
        assertEquals(1.0F, high.strength());
        assertEquals(0.0F, invalid.strength());
        assertNotSame(low, high);
    }

    @Test
    void rejectsNullFilterIdentity() {
        assertThrows(NullPointerException.class, () ->
                new com.xkmxz.prismod.api.client.contract.FilterSnapshot(null, 1.0F, false, true));
    }

    @Test
    void convertsBetweenClientAndCommonSnapshotModelsExplicitly() {
        ResourceLocation effective = ResourceLocation.fromNamespaceAndPath("example", "debug");
        ResourceLocation selected = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        com.xkmxz.prismod.api.client.contract.FilterSnapshot client =
                new com.xkmxz.prismod.api.client.contract.FilterSnapshot(
                        effective, 0.75F, true, false, selected, 0.5F,
                        "example-mod", 42,
                        com.xkmxz.prismod.api.common.state.filter.FilterFallbackReason.RENDER_UNAVAILABLE,
                        9L);

        com.xkmxz.prismod.api.common.model.FilterSnapshot common = client.toCommonSnapshot();
        assertEquals(effective, common.filter());
        assertEquals(selected, common.selectedFilter());
        assertEquals(42, common.overridePriority());
        assertEquals(9L, common.generation());

        com.xkmxz.prismod.api.client.contract.FilterSnapshot restored =
                com.xkmxz.prismod.api.client.contract.FilterSnapshot.fromCommonSnapshot(common, true);
        assertEquals(client.filter(), restored.filter());
        assertEquals(client.selectedStrength(), restored.selectedStrength());
        assertEquals(client.overrideOwner(), restored.overrideOwner());
        assertTrue(restored.forced());
    }
}
