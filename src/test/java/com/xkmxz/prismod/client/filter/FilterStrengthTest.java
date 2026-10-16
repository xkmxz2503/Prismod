package com.xkmxz.prismod.client.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterStrengthTest {
    @Test
    void finiteStrengthsAreClampedToShaderRange() {
        assertEquals(0.0F, FilterStrength.normalize(-3.0F));
        assertEquals(100.0F, FilterStrength.normalize(300.0F));
        assertEquals(37.5F, FilterStrength.normalize(37.5F));
    }

    @Test
    void allNonFiniteInputsBecomeZero() {
        assertEquals(0.0F, FilterStrength.normalize(Double.NaN));
        assertEquals(0.0F, FilterStrength.normalize(Double.POSITIVE_INFINITY));
        assertEquals(0.0F, FilterStrength.normalize(Double.NEGATIVE_INFINITY));
    }

    @Test
    void veryLargeFiniteDoubleIsClampedBeforeFloatConversion() {
        assertEquals(100.0F, FilterStrength.normalize(Double.MAX_VALUE));
        assertEquals(0.0F, FilterStrength.normalize(-Double.MAX_VALUE));
    }
}
