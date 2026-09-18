package com.xkmxz.prismod.client.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilterKeyTest {
    @Test
    void legacyShortNamesMapToPrismodNamespace() {
        assertEquals("prismod:warm", FilterKey.parse("warm").serializedName());
        assertEquals("prismod:original", FilterKey.parse("original").serializedName());
    }

    @Test
    void namespacedIdsRemainUnchanged() {
        assertEquals("example:debug/scan", FilterKey.parse("example:debug/scan").serializedName());
    }

    @Test
    void malformedIdsAreRejected() {
        assertNull(FilterKey.parse("example:bad id"));
        assertNull(FilterKey.parse(""));
    }
}
