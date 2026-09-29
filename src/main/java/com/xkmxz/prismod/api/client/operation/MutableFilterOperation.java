package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;

import java.util.UUID;

/** Internal mutable implementation exposed through the immutable FilterOperation contract. */
final class MutableFilterOperation implements FilterOperation {
    private final UUID id;
    private volatile FilterOperationStatus status = FilterOperationStatus.QUEUED;
    private volatile FeedbackCode code = FeedbackCode.OK;
    private volatile int affectedCount;

    MutableFilterOperation(UUID id) {
        this.id = id == null ? UUID.randomUUID() : id;
    }

    void complete(FilterOperationStatus status, FeedbackCode code, int affectedCount) {
        this.status = status == null ? FilterOperationStatus.FAILED : status;
        this.code = code == null ? FeedbackCode.FAILED : code;
        this.affectedCount = Math.max(0, affectedCount);
    }

    @Override public UUID id() { return id; }
    @Override public FilterOperationStatus status() { return status; }
    @Override public FeedbackCode code() { return code; }
    @Override public int affectedCount() { return affectedCount; }
    @Override public boolean completed() { return status != FilterOperationStatus.QUEUED; }
}
