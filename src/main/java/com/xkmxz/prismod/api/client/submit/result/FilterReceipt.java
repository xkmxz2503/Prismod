package com.xkmxz.prismod.api.client.submit.result;

import com.xkmxz.prismod.api.common.submit.result.FeedbackCode;
import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 一次性最终回执；成功表示业务提交完成，不代表 GPU 已完成渲染。 */
public record FilterReceipt(UUID requestId, FilterOperationStatus status, FeedbackCode code,
                            FilterFailureReason failureReason, String failureDetail,
                            int affectedCount, List<UUID> resourceIds, List<FilterReceipt> items) {
    public FilterReceipt {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(failureReason, "failureReason");
        if (status == FilterOperationStatus.QUEUED) {
            throw new IllegalArgumentException("最终回执不能处于 QUEUED");
        }
        if (affectedCount < 0) throw new IllegalArgumentException("affectedCount");
        failureDetail = failureDetail == null ? "" : failureDetail;
        resourceIds = List.copyOf(resourceIds);
        items = List.copyOf(items);
    }
}
