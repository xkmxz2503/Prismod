package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.client.filter.FilterState;

/** Dynamic filter state used internally by the client renderer and controller. */
public record FilterSelection(FilterKey key, float strength, boolean forced) {
    public FilterSelection {
        key = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
        strength = FilterState.normalizeStrength(strength);
    }
}
