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

}
