package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;
import com.xkmxz.prismod.api.common.result.FeedbackCode;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;

import java.util.UUID;

/**
 * Observable result of a client API operation that may be queued to the client thread.
 *
 * <p>A queued operation eventually reaches one terminal status. Reading this
 * object is safe while the client thread completes the operation.</p>
 */
public interface FilterOperation {
    /** @return immutable successful zero-impact operation for compatibility handles */
    static FilterOperation completedOperation() {
        return CompletedFilterOperation.INSTANCE;
    }

    /** Creates an already completed failed operation for validation failures. */
    static FilterOperation failedOperation(FilterOperationStatus status, FeedbackCode code,
                                           FilterFailureReason reason, String detail) {
        return new FixedFilterOperation(status, code, reason, detail);
    }

    /** @return stable operation identifier */
    UUID id();

    /** @return current status, such as {@code QUEUED}, {@code SUCCESS}, or {@code FAILED} */
    FilterOperationStatus status();

    /** @return stable machine-readable result code */
    FeedbackCode code();

    /** @return number of affected items, or zero for no-op operations */
    int affectedCount();

    /** @return whether this operation has reached a terminal status */
    boolean completed();

    /** @return stable failure category, or {@link FilterFailureReason#NONE} */
    default FilterFailureReason failureReason() {
        return FilterFailureReason.NONE;
    }

    /** @return short diagnostic detail for {@link #failureReason()} */
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
