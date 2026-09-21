package com.xkmxz.prismod.api.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterSnapshotTest {
    @Test
    void preservesCompleteCustomFilterIdentity() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");
        FilterSnapshot snapshot = new FilterSnapshot(id, 0.75F, true, true);
        assertEquals(id, snapshot.filter());
        assertEquals("example:debug", snapshot.filter().toString());
        assertEquals(0.75F, snapshot.strength());
        assertTrue(snapshot.forced());
        assertTrue(snapshot.renderAvailable());
    }

    @Test
    void normalizesStrengthAndKeepsSnapshotImmutable() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "warm");
        FilterSnapshot low = new FilterSnapshot(id, -1.0F, false, true);
        FilterSnapshot high = new FilterSnapshot(id, 2.0F, false, true);
        FilterSnapshot invalid = new FilterSnapshot(id, Float.NaN, false, false);
        assertEquals(0.0F, low.strength());
        assertEquals(1.0F, high.strength());
        assertEquals(0.0F, invalid.strength());
        assertNotSame(low, high);
    }

    @Test
    void rejectsNullFilterIdentity() {
        assertThrows(NullPointerException.class, () -> new FilterSnapshot(null, 1.0F, false, true));
    }
}
