package com.xkmxz.prismod.api.common.result;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;

import java.util.Map;
import java.util.UUID;

/** Immutable, language-independent result shared by API and command adapters. */
public record OperationResult(
        FilterOperationStatus status,
        FeedbackCode code,
        UUID requestId,
        String target,
        int affectedCount,
        Map<String, Object> parameters,
        long generation
) {
    public OperationResult {
        status = status == null ? FilterOperationStatus.FAILED : status;
        code = code == null ? FeedbackCode.FAILED : code;
        requestId = requestId == null ? new UUID(0L, 0L) : requestId;
        target = target == null ? "" : target;
        affectedCount = Math.max(0, affectedCount);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public boolean accepted() {
        return status == FilterOperationStatus.SUCCESS;
    }

    public static OperationResult success(UUID requestId, String target, int affectedCount, long generation) {
        return new OperationResult(FilterOperationStatus.SUCCESS, FeedbackCode.OK, requestId,
                target, affectedCount, Map.of(), generation);
    }

    public static OperationResult failure(FilterOperationStatus status, FeedbackCode code,
                                          String target, long generation) {
        return new OperationResult(status, code, new UUID(0L, 0L), target, 0, Map.of(), generation);
    }
}
