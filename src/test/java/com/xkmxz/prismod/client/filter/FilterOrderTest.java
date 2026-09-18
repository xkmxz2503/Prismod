package com.xkmxz.prismod.client.filter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class FilterOrderTest {
    private static final List<String> CUSTOM = List.of("night_vision", "vintage", "cool", "warm", "grayscale", "original");

    @Test
    void preservesACompleteCustomOrder() {
        List<String> warnings = new ArrayList<>();
        List<FilterId> result = FilterId.parseCycleOrder(CUSTOM, warnings::add);
        assertEquals(FilterId.NIGHT_VISION, result.get(0));
        assertEquals(FilterId.ORIGINAL, result.get(5));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void unknownItemIsNotDiscardedFromAnOtherwiseCompleteOrder() {
        List<String> malformed = new ArrayList<>(CUSTOM);
        malformed.add("not_a_filter");
        assertInvalid(malformed);
    }

    @Test
    void duplicateMakesEntireOrderFallBack() {
        assertInvalid(List.of("night_vision", "vintage", "cool", "warm", "grayscale", "night_vision"));
    }

    @Test
    void missingItemMakesEntireOrderFallBack() {
        assertInvalid(CUSTOM.subList(0, 5));
    }

    @Test
    void unknownReplacementMakesEntireOrderFallBack() {
        assertInvalid(List.of("night_vision", "vintage", "cool", "warm", "grayscale", "unknown"));
    }

    @Test
    void emptyOrNonListValuesFallBackWithOneWarning() {
        assertInvalid(List.of());
        assertInvalid(null);
        assertInvalid("original");
    }

    @Test
    void wrongElementTypeAndNullAreRejectedWithoutClassCastErrors() {
        assertInvalid(List.of("night_vision", "vintage", "cool", "warm", "grayscale", 12));
        assertInvalid(Arrays.asList("night_vision", "vintage", "cool", "warm", "grayscale", null));
    }

    @Test
    void parsingIsIndependentOfSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(FilterId.NIGHT_VISION, FilterId.fromSerialized("NIGHT_VISION"));
            assertEquals(FilterId.VINTAGE, FilterId.fromSerialized("VINTAGE"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void parsedAndDefaultOrdersCannotBeMutated() {
        List<String> mutable = new ArrayList<>(CUSTOM);
        List<FilterId> parsed = FilterId.parseCycleOrder(mutable, message -> fail(message));
        mutable.clear();
        assertEquals(6, parsed.size());
        assertThrows(UnsupportedOperationException.class, () -> parsed.add(FilterId.WARM));
        assertThrows(UnsupportedOperationException.class, () -> FilterId.defaultOrder().clear());
    }

    private static void assertInvalid(Object input) {
        List<String> warnings = new ArrayList<>();
        assertEquals(FilterId.defaultOrder(), FilterId.parseCycleOrder(input, warnings::add));
        assertEquals(1, warnings.size());
        assertFalse(warnings.get(0).isBlank());
    }
}
