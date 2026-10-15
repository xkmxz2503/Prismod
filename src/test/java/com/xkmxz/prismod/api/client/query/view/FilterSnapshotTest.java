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
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, 0.75F, true, true);
        assertEquals(id, snapshot.filter());
        assertEquals("example:debug", snapshot.filter().toString());
        assertEquals(0.75F, snapshot.strength());
        assertTrue(snapshot.forced());
        assertTrue(snapshot.renderAvailable());
    }

    @Test
    void normalizesStrengthAndKeepsSnapshotImmutable() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot low =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, -1.0F, false, true);
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot high =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, 2.0F, false, true);
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot invalid =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(id, Float.NaN, false, false);
        assertEquals(0.0F, low.strength());
        assertEquals(1.0F, high.strength());
        assertEquals(0.0F, invalid.strength());
        assertNotSame(low, high);
    }

    @Test
    void rejectsNullFilterIdentity() {
        assertThrows(NullPointerException.class, () ->
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(null, 1.0F, false, true));
    }

    @Test
    void convertsBetweenClientAndCommonSnapshotModelsExplicitly() {
        ResourceLocation effective = ResourceLocation.fromNamespaceAndPath("example", "debug");
        ResourceLocation selected = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        com.xkmxz.prismod.api.client.query.view.FilterSnapshot client =
                new com.xkmxz.prismod.api.client.query.view.FilterSnapshot(
                        effective, 0.75F, true, false, selected, 0.5F,
                        "example-mod", 42,
                        com.xkmxz.prismod.api.common.state.filter.FilterFallbackReason.RENDER_UNAVAILABLE,
                        9L);

        com.xkmxz.prismod.api.common.model.FilterSnapshot common = client.toCommonSnapshot();
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
