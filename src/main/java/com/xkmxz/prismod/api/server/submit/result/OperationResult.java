package com.xkmxz.prismod.api.server.submit.result;

import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.submit.result.FeedbackCode;
import com.xkmxz.prismod.api.common.submit.state.operation.FilterOperationStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 服务端一次性最终回执。成功仅表示服务端接受并保存策略，不保证网络送达或完成渲染。
 * @param requestId 本次提交的独立 UUID，失败也具有独立身份
 * @param status 最终执行状态，不允许 QUEUED
 * @param code 稳定反馈代码
 * @param failureReason 结构化失败分类
 * @param failureDetail 诊断详情，不作为稳定业务判断依据
 * @param target broadcast、玩家 UUID、all 或参数非法时的空字符串
 * @param affectedCount 影响的策略数量
 * @param policyIds 新建或更新的策略 UUID；清理和失败时为空
 * @param generation 本次操作结束后的服务端状态代数
 */
public record OperationResult(UUID requestId, FilterOperationStatus status, FeedbackCode code,
                              FilterFailureReason failureReason, String failureDetail, String target,
                              int affectedCount, List<UUID> policyIds, long generation) {
    public OperationResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(failureReason, "failureReason");
        if (status == FilterOperationStatus.QUEUED) throw new IllegalArgumentException("最终回执不能为 QUEUED");
        if (affectedCount < 0) throw new IllegalArgumentException("affectedCount");
        failureDetail = failureDetail == null ? "" : failureDetail;
        target = target == null ? "" : target;
        policyIds = List.copyOf(policyIds);
    }
    /** 仅表示服务端成功接受本次操作。 */
    public boolean accepted() { return status == FilterOperationStatus.SUCCESS; }
}
