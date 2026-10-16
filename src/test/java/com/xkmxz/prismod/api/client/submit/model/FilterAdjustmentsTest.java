package com.xkmxz.prismod.api.client.submit.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterAdjustmentsTest {
    @Test
    void builderKeepsUnsetFieldsUntouched() {
        FilterAdjustments value = FilterAdjustments.builder().exposure(50.0F).build();
        assertEquals(50.0F, value.exposure());
        assertNull(value.contrast());
        assertFalse(value.clearsContrast());
    }

    @Test
    void builderClampsAndHandlesNonFiniteValues() {
        FilterAdjustments value = FilterAdjustments.builder()
                .exposure(150.0F).contrast(Float.NaN).saturation(Float.POSITIVE_INFINITY)
                .gamma(-1.0F).build();
        assertEquals(100.0F, value.exposure());
        assertEquals(100.0F, value.contrast());
        assertEquals(100.0F, value.saturation());
        assertEquals(10.0F, value.gamma());
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
        FilterAdjustments previous = FilterAdjustments.builder().exposure(25.0F).contrast(140.0F).build();
        FilterAdjustments update = FilterAdjustments.builder().exposure(50.0F).clearContrast().build();
        FilterAdjustments merged = FilterAdjustments.merge(previous, update);
        assertEquals(50.0F, merged.exposure());
        assertNull(merged.contrast());
        assertFalse(merged.clearsContrast());
    }

    @Test
    void builderProducesIndependentImmutableValues() {
        FilterAdjustments.Builder builder = FilterAdjustments.builder().exposure(25.0F);
        FilterAdjustments first = builder.build();
        builder.exposure(50.0F);
        assertEquals(25.0F, first.exposure());
    }
}
