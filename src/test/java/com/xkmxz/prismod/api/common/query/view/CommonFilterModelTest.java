package com.xkmxz.prismod.api.common.query.view;

import com.xkmxz.prismod.api.common.query.state.filter.FilterFallbackReason;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonFilterModelTest {
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
