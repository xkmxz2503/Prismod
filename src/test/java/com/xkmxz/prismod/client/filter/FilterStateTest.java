package com.xkmxz.prismod.client.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterStateTest {
    @Test
    void finiteStrengthsAreClampedToShaderRange() {
        assertEquals(0.0F, new FilterState(FilterId.WARM, -3.0F, false).strength());
        assertEquals(1.0F, new FilterState(FilterId.WARM, 3.0F, false).strength());
        assertEquals(0.375F, new FilterState(FilterId.WARM, 0.375F, false).strength());
    }

    @Test
    void allNonFiniteInputsBecomeZero() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(0.0F, new FilterState(FilterId.COOL, value, true).strength());
        }
        assertEquals(0.0F, FilterState.normalizeStrength(Double.NaN));
        assertEquals(0.0F, FilterState.normalizeStrength(Double.POSITIVE_INFINITY));
        assertEquals(0.0F, FilterState.normalizeStrength(Double.NEGATIVE_INFINITY));
    }

    @Test
    void veryLargeFiniteDoubleIsClampedBeforeFloatConversion() {
        assertEquals(1.0F, FilterState.normalizeStrength(Double.MAX_VALUE));
        assertEquals(0.0F, FilterState.normalizeStrength(-Double.MAX_VALUE));
    }

    @Test
    void nullFilterUsesOriginalWithoutLosingForcedMarker() {
        assertEquals(new FilterState(FilterId.ORIGINAL, 0.5F, true), new FilterState(null, 0.5F, true));
    }
}
