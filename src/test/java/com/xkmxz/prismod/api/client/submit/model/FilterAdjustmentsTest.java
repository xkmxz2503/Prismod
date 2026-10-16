package com.xkmxz.prismod.api.client.submit.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterAdjustmentsTest {
    @Test
    void builderKeepsUnsetFieldsUntouched() {
        FilterAdjustments value = FilterAdjustments.builder().exposure(1.0F).build();
        assertEquals(1.0F, value.exposure());
        assertNull(value.contrast());
        assertFalse(value.clearsContrast());
    }

    @Test
    void builderClampsAndHandlesNonFiniteValues() {
        FilterAdjustments value = FilterAdjustments.builder()
                .exposure(10.0F).contrast(Float.NaN).saturation(Float.POSITIVE_INFINITY)
                .gamma(-1.0F).build();
        assertEquals(2.0F, value.exposure());
        assertEquals(0.0F, value.contrast());
        assertEquals(1.0F, value.saturation());
        assertEquals(0.1F, value.gamma());
    }

    @Test
    void clearMarksFieldWithoutSupplyingValue() {
        FilterAdjustments value = FilterAdjustments.builder().clearExposure().build();
        assertNull(value.exposure());
        assertTrue(value.clearsExposure());
        assertFalse(value.isEmpty());
    }

    @Test
    void mergeOnlyChangesTouchedFields() {
        FilterAdjustments previous = FilterAdjustments.builder().exposure(1.0F).contrast(.4F).build();
        FilterAdjustments update = FilterAdjustments.builder().exposure(1.5F).clearContrast().build();
        FilterAdjustments merged = FilterAdjustments.merge(previous, update);
        assertEquals(1.5F, merged.exposure());
        assertNull(merged.contrast());
        assertFalse(merged.clearsContrast());
    }

    @Test
    void builderProducesIndependentImmutableValues() {
        FilterAdjustments.Builder builder = FilterAdjustments.builder().exposure(1.0F);
        FilterAdjustments first = builder.build();
        builder.exposure(2.0F);
        assertEquals(1.0F, first.exposure());
    }
}
