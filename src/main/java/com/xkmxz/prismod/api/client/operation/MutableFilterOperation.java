package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Internal mutable implementation exposed through the immutable FilterOperation contract. */
final class MutableFilterOperation implements FilterOperation {
    private final UUID id;
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile FilterOperationStatus status = FilterOperationStatus.QUEUED;
    private volatile FeedbackCode code = FeedbackCode.OK;
    private volatile int affectedCount;
    private volatile FilterFailureReason failureReason = FilterFailureReason.NONE;
    private volatile String failureDetail = "";

    MutableFilterOperation(UUID id) {
        this.id = id == null ? UUID.randomUUID() : id;
    }

    void complete(FilterOperationStatus status, FeedbackCode code, int affectedCount) {
        complete(status, code, FilterFailureReason.NONE, "", affectedCount);
    }

    synchronized void complete(FilterOperationStatus status, FeedbackCode code,
                                FilterFailureReason failureReason, String failureDetail, int affectedCount) {
        if (completed.get()) return;
        this.status = status == null ? FilterOperationStatus.FAILED : status;
        this.code = code == null ? FeedbackCode.FAILED : code;
        this.failureReason = failureReason == null ? FilterFailureReason.UNKNOWN : failureReason;
        this.failureDetail = failureDetail == null ? "" : failureDetail;
        this.affectedCount = Math.max(0, affectedCount);
        completed.set(true);
    }

    @Override public UUID id() { return id; }
    @Override public FilterOperationStatus status() { return status; }
    @Override public FeedbackCode code() { return code; }
    @Override public int affectedCount() { return affectedCount; }
    @Override public boolean completed() { return completed.get(); }
    @Override public FilterFailureReason failureReason() { return failureReason; }
    @Override public String failureDetail() { return failureDetail; }
}
