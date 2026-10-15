package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Aggregate operation for a best-effort client batch. */
public final class FilterBatchOperation implements FilterOperation {
    private final UUID id;
    private final List<FilterOperation> items;
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile FilterOperationStatus status;
    private volatile FeedbackCode code;
    private volatile int affectedCount;
    private volatile int successfulCount;
    private volatile int failedCount;
    private volatile FilterFailureReason failureReason;
    private volatile String failureDetail;

    public FilterBatchOperation(UUID id, List<FilterOperation> items, FilterOperationStatus status,
                                FeedbackCode code, int affectedCount, int successfulCount, int failedCount,
                                FilterFailureReason failureReason, String failureDetail) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.items = List.copyOf(items == null ? List.of() : items);
        this.status = status == null ? FilterOperationStatus.QUEUED : status;
        this.code = code == null ? FeedbackCode.OK : code;
        this.affectedCount = Math.max(0, affectedCount);
        this.successfulCount = Math.max(0, successfulCount);
        this.failedCount = Math.max(0, failedCount);
        this.failureReason = failureReason == null ? FilterFailureReason.NONE : failureReason;
        this.failureDetail = failureDetail == null ? "" : failureDetail;
        if (status != null && status != FilterOperationStatus.QUEUED) completed.set(true);
    }

    /** @return immutable operations in the same order as the input batch */
    public List<FilterOperation> items() { return items; }
    /** @return number of successful items observed so far */
    public int successfulCount() { return successfulCount; }
    /** @return number of failed items observed so far */
    public int failedCount() { return failedCount; }

    public synchronized void complete(FilterOperationStatus status, FeedbackCode code, int affectedCount,
                                      int successfulCount, int failedCount,
                                      FilterFailureReason failureReason, String failureDetail) {
        if (completed.get()) return;
        this.status = status == null ? FilterOperationStatus.FAILED : status;
        this.code = code == null ? FeedbackCode.FAILED : code;
        this.affectedCount = Math.max(0, affectedCount);
        this.successfulCount = Math.max(0, successfulCount);
        this.failedCount = Math.max(0, failedCount);
        this.failureReason = failureReason == null ? FilterFailureReason.NONE : failureReason;
        this.failureDetail = failureDetail == null ? "" : failureDetail;
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
