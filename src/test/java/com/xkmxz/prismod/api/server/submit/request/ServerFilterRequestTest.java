package com.xkmxz.prismod.api.server.submit.request;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerFilterRequestTest {
    @Test
    void normalizesCommonOverrideRequest() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");
        FilterOverrideRequest request = new FilterOverrideRequest(" example-mod ", id, 200.0F, 7);

        assertEquals("example-mod", request.ownerId());
        assertEquals(id, request.filter());
        assertEquals(100.0F, request.strength());
        assertEquals(7, request.priority());
    }

    @Test
    void normalizesSelectionStrengthAndRejectsNullFilter() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");

        assertEquals(0.0F, new FilterSelectionRequest(id, Float.NaN).strength());
        assertEquals(100.0F, new FilterSelectionRequest(id, 200.0F).strength());
        assertThrows(NullPointerException.class, () -> new FilterSelectionRequest(null, 0.5F));
    }

    @Test
    void rejectsBlankOverrideOwnerAndNullFilter() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");

        assertThrows(IllegalArgumentException.class, () -> new FilterOverrideRequest("  ", id, 0.5F, 1));
        assertThrows(NullPointerException.class, () -> new FilterOverrideRequest("owner", null, 0.5F, 1));
    }

}
