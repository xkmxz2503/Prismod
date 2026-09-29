package com.xkmxz.prismod.api.common.model;

import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.state.filter.FilterFallbackReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonFilterModelTest {
    @Test
    void normalizesCommonOverrideRequest() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "debug");
        FilterOverrideRequest request = new FilterOverrideRequest(" example-mod ", id, 2.0F, 7);

        assertEquals("example-mod", request.ownerId());
        assertEquals(id, request.filter());
        assertEquals(1.0F, request.strength());
        assertEquals(7, request.priority());
    }

    @Test
    void snapshotCarriesFallbackAndOverrideMetadata() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "original");
        FilterSnapshot snapshot = new FilterSnapshot(
                id, 0.0F, id, 0.0F, "example-mod", 4,
                true, FilterFallbackReason.RENDER_UNAVAILABLE, 12);

        assertEquals("example-mod", snapshot.overrideOwner());
        assertEquals(FilterFallbackReason.RENDER_UNAVAILABLE, snapshot.fallbackReason());
        assertEquals(12, snapshot.generation());
    }
}
