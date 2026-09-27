package com.xkmxz.prismod.api.common.state;

/** Result state for a client or server-facing filter operation. */
public enum FilterOperationStatus {
    SUCCESS,
    QUEUED,
    INVALID_ARGUMENT,
    NOT_FOUND,
    UNAVAILABLE,
    CLOSED,
    FAILED
}
