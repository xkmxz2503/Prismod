package com.xkmxz.prismod.api.common.state.filter;

/** Explains why the effective filter is not the requested filter. */
public enum FilterFallbackReason {
    NONE,
    DISABLED,
    UNAVAILABLE,
    RENDER_UNAVAILABLE,
    INVALID_OVERRIDE
}
