package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;

import java.util.UUID;

/** Observable result of a client API operation that may be queued to the client thread. */
public interface FilterOperation {
    UUID id();

    FilterOperationStatus status();

    FeedbackCode code();

    int affectedCount();

    boolean completed();
}
