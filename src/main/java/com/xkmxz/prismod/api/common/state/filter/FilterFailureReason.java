package com.xkmxz.prismod.api.common.state.filter;

/** Stable failure categories exposed without leaking implementation exceptions. */
public enum FilterFailureReason {
    NONE,
    INVALID_ARGUMENT,
    ID_CONFLICT,
    RESOURCE_MISSING,
    RESOURCE_INVALID,
    RENDER_FAILED,
    UNKNOWN
}
