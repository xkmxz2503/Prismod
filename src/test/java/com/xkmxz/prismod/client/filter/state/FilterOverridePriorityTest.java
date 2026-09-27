package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterOverridePriorityTest {
    @Test
    void higherPriorityOverrideWinsAndClosingItRestoresNextOverride() {
        FilterController controller = new FilterController();
        controller.addOverride("low", FilterKey.of(FilterId.WARM), 0.2F, 1);
        long high = controller.addOverride("high", FilterKey.of(FilterId.COOL), 0.8F, 5);

        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
        controller.removeOverride(high);
        assertEquals(FilterKey.of(FilterId.WARM), controller.effectiveSelection().key());
    }

    @Test
    void samePriorityUsesNewestOverride() {
        FilterController controller = new FilterController();
        controller.addOverride("first", FilterKey.of(FilterId.WARM), 0.2F, 3);
        controller.addOverride("second", FilterKey.of(FilterId.COOL), 0.8F, 3);

        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
    }
}
