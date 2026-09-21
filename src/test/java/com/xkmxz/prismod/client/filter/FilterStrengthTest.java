package com.xkmxz.prismod.client.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterStrengthTest {
    @Test
    void finiteStrengthsAreClampedToShaderRange() {
        assertEquals(0.0F, FilterStrength.normalize(-3.0F));
        assertEquals(1.0F, FilterStrength.normalize(3.0F));
        assertEquals(0.375F, FilterStrength.normalize(0.375F));
    }

    @Test
    void allNonFiniteInputsBecomeZero() {
        assertEquals(0.0F, FilterStrength.normalize(Double.NaN));
        assertEquals(0.0F, FilterStrength.normalize(Double.POSITIVE_INFINITY));
        assertEquals(0.0F, FilterStrength.normalize(Double.NEGATIVE_INFINITY));
    }

    @Test
    void veryLargeFiniteDoubleIsClampedBeforeFloatConversion() {
        assertEquals(1.0F, FilterStrength.normalize(Double.MAX_VALUE));
        assertEquals(0.0F, FilterStrength.normalize(-Double.MAX_VALUE));
    }
}
