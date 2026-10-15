package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;

import java.util.UUID;

/** Observable result of a client API operation that may be queued to the client thread. */
public interface FilterOperation {
    static FilterOperation completedOperation() {
        return CompletedFilterOperation.INSTANCE;
    }

    static FilterOperation failedOperation(FilterOperationStatus status, FeedbackCode code,
                                           FilterFailureReason reason, String detail) {
        return new FixedFilterOperation(status, code, reason, detail);
    }

    UUID id();

    FilterOperationStatus status();

    FeedbackCode code();

    int affectedCount();

    boolean completed();

    default FilterFailureReason failureReason() {
        return FilterFailureReason.NONE;
    }

    default String failureDetail() {
        return "";
    }
}

final class CompletedFilterOperation implements FilterOperation {
    static final FilterOperation INSTANCE = new CompletedFilterOperation();
    private final UUID id = UUID.randomUUID();

    CompletedFilterOperation() {
    }

    @Override public UUID id() { return id; }
    @Override public FilterOperationStatus status() { return FilterOperationStatus.SUCCESS; }
    @Override public FeedbackCode code() { return FeedbackCode.OK; }
    @Override public int affectedCount() { return 0; }
    @Override public boolean completed() { return true; }
}

final class FixedFilterOperation implements FilterOperation {
    private final UUID id = UUID.randomUUID();
    private final FilterOperationStatus status;
    private final FeedbackCode code;
    private final FilterFailureReason failureReason;
    private final String failureDetail;

    FixedFilterOperation(FilterOperationStatus status, FeedbackCode code,
                         FilterFailureReason failureReason, String failureDetail) {
        this.status = status == null ? FilterOperationStatus.FAILED : status;
        this.code = code == null ? FeedbackCode.FAILED : code;
        this.failureReason = failureReason == null ? FilterFailureReason.UNKNOWN : failureReason;
        this.failureDetail = failureDetail == null ? "" : failureDetail;
    }

    @Override public UUID id() { return id; }
    @Override public FilterOperationStatus status() { return status; }
    @Override public FeedbackCode code() { return code; }
    @Override public int affectedCount() { return 0; }
    @Override public boolean completed() { return true; }
    @Override public FilterFailureReason failureReason() { return failureReason; }
    @Override public String failureDetail() { return failureDetail; }
}
