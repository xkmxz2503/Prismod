package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;

import java.util.UUID;

/** Observable result of a client API operation that may be queued to the client thread. */
public interface FilterOperation {
    static FilterOperation completedOperation() {
        return new CompletedFilterOperation(UUID.randomUUID());
    }

    UUID id();

    FilterOperationStatus status();

    FeedbackCode code();

    int affectedCount();

    boolean completed();
}

final class CompletedFilterOperation implements FilterOperation {
    private final UUID id;

    CompletedFilterOperation(UUID id) {
        this.id = id;
    }

    @Override public UUID id() { return id; }
    @Override public FilterOperationStatus status() { return FilterOperationStatus.SUCCESS; }
    @Override public FeedbackCode code() { return FeedbackCode.OK; }
    @Override public int affectedCount() { return 0; }
    @Override public boolean completed() { return true; }
}
