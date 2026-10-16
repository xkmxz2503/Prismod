package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void winningOverrideCarriesItsPartialAdjustmentsAndRestoresNextOverride() {
        FilterAdjustments lowAdjustments = FilterAdjustments.builder().exposure(0.5F).build();
        FilterAdjustments highAdjustments = FilterAdjustments.builder().saturation(1.4F).build();
        controllerWithOverrides(lowAdjustments, highAdjustments);
    }

    private static void controllerWithOverrides(FilterAdjustments lowAdjustments,
                                                FilterAdjustments highAdjustments) {
        FilterController controller = new FilterController();
        controller.addOverride("low", FilterKey.of(FilterId.WARM), 0.2F, 1, lowAdjustments);
        long high = controller.addOverride("high", FilterKey.of(FilterId.COOL), 0.8F, 5, highAdjustments);
        assertSame(highAdjustments, controller.effectiveSelection().adjustments());
        controller.removeOverride(high);
        assertSame(lowAdjustments, controller.effectiveSelection().adjustments());
    }
}
