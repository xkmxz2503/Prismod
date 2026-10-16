package com.xkmxz.prismod.api.client.query.view;

import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterSnapshotTest {
    @Test
    void preservesCompleteCustomFilterIdentity() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot snapshot =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, 75F, true, true);
        assertEquals(id, snapshot.filter());
        assertEquals("example:debug", snapshot.filter().toString());
        assertEquals(75F, snapshot.strength());
        assertTrue(snapshot.forced());
        assertTrue(snapshot.renderAvailable());
    }

    @Test
    void normalizesStrengthAndKeepsSnapshotImmutable() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot low =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, -100F, false, true);
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot high =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, 200F, false, true);
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot invalid =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, Float.NaN, false, false);
        assertEquals(0.0F, low.strength());
        assertEquals(100F, high.strength());
        assertEquals(0.0F, invalid.strength());
        assertNotSame(low, high);
    }

    @Test
    void rejectsNullFilterIdentity() {
        assertThrows(NullPointerException.class, () ->
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(null, 100F, false, true));
    }

    @Test
    void convertsBetweenClientAndCommonSnapshotModelsExplicitly() {
        ResourceLocation effective = ResourceLocation.fromNamespaceAndPath("example", "debug");
        ResourceLocation selected = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot client =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(
                        effective, 75F, true, false, selected, 50F,
                        "example-mod", 42,
                        com.xkmxz.prismod.api.common.query.state.filter.FilterFallbackReason.RENDER_UNAVAILABLE,
                        9L);

        com.xkmxz.prismod.api.common.query.view.FilterSnapshot common = client.toCommonSnapshot();
        assertEquals(effective, common.filter());
        assertEquals(selected, common.selectedFilter());
        assertEquals(42, common.overridePriority());
        assertEquals(9L, common.generation());

        com.xkmxz.prismod.api.client.query.view.FilterSnapshot restored =
                com.xkmxz.prismod.api.client.query.view.FilterSnapshot.fromCommonSnapshot(common, true);
        assertEquals(client.filter(), restored.filter());
        assertEquals(client.selectedStrength(), restored.selectedStrength());
        assertEquals(client.overrideOwner(), restored.overrideOwner());
        assertTrue(restored.forced());
    }
}
