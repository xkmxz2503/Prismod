package com.xkmxz.prismod.api.common.state;

/** Stable failure categories exposed without leaking implementation exceptions. */
public enum FilterFailureReason {
    NONE,
    INVALID_ARGUMENT,
    RESOURCE_MISSING,
    RESOURCE_INVALID,
    RENDER_FAILED,
    UNKNOWN
}
