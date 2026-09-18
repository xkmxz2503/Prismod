package com.xkmxz.prismod.client.filter;

/** Dynamic filter state used internally by the client renderer and controller. */
public record FilterSelection(FilterKey key, float strength, boolean forced) {
    public FilterSelection {
        key = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
        strength = FilterState.normalizeStrength(strength);
    }
}
