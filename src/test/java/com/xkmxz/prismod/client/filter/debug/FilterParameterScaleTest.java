package com.xkmxz.prismod.client.filter.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterParameterScaleTest {
    @Test void convertsPercentageToShaderRanges() {
        assertEquals(0.0F, FilterParameterScale.strength(0));
        assertEquals(1.0F, FilterParameterScale.strength(100));
        assertEquals(-2.0F, FilterParameterScale.exposure(-100));
        assertEquals(0.0F, FilterParameterScale.exposure(0));
        assertEquals(2.0F, FilterParameterScale.exposure(100));
        assertEquals(-1.0F, FilterParameterScale.contrast(0));
        assertEquals(0.0F, FilterParameterScale.contrast(100));
        assertEquals(1.0F, FilterParameterScale.contrast(200));
        assertEquals(0.0F, FilterParameterScale.saturation(0));
        assertEquals(1.0F, FilterParameterScale.saturation(100));
        assertEquals(2.0F, FilterParameterScale.saturation(200));
        assertEquals(0.1F, FilterParameterScale.gamma(10));
        assertEquals(1.0F, FilterParameterScale.gamma(100));
        assertEquals(3.0F, FilterParameterScale.gamma(300));
    }
}
