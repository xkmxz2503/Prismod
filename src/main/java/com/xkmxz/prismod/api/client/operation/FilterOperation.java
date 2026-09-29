package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.FilterOperationStatus;

import java.util.UUID;

/** Observable result of a client API operation that may be queued to the client thread. */
public interface FilterOperation {
    UUID id();

    FilterOperationStatus status();

    int affectedCount();

    String detail();

    boolean completed();
}
