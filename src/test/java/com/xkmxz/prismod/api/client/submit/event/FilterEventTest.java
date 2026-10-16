package com.xkmxz.prismod.api.client.submit.event;

import com.xkmxz.prismod.api.common.query.view.FilterDescriptor;
import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.query.state.filter.FilterType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterEventTest {
    @Test
    void registryEventDefensivelyCopiesTheRuntimeList() {
        List<FilterDescriptor> filters = new ArrayList<>();
        filters.add(new FilterDescriptor(ResourceLocation.fromNamespaceAndPath("example", "test"), "owner",
                FilterType.POST_CHAIN, "filter.example.test", 100.0F, true, FilterFailureReason.NONE, ""));

        FilterEvent event = FilterEvent.registryChanged(filters);
        filters.clear();

        assertEquals(1, event.filters().size());
        assertThrows(UnsupportedOperationException.class, () -> event.filters().clear());
    }
}
