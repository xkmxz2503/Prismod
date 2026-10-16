package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.client.filter.FilterStrength;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;

/** Dynamic filter state used internally by the client renderer and controller. */
public record FilterSelection(FilterKey key, float strength, boolean forced, FilterAdjustments adjustments) {
    public FilterSelection(FilterKey key, float strength, boolean forced) {
        this(key, strength, forced, null);
    }

    public FilterSelection {
        key = key == null ? FilterKey.of(FilterId.ORIGINAL) : key;
        strength = FilterStrength.normalize(strength);
    }
}
