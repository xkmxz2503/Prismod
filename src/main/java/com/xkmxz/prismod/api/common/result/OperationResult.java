package com.xkmxz.prismod.api.common.result;

import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;

import java.util.Map;
import java.util.UUID;

/**
 * API 操作的不可变结果，供公开 API、命令适配器和网络适配器共享。
 *
 * <p>该结果描述的是调用方所在这一层对操作的处理结果。对于服务端 API，
 * {@link #status()} 表示服务端是否接受并保存了策略；它不表示客户端已经完成
 * 网络接收或渲染。</p>
 *
 * <p>{@link FilterOperationStatus#SUCCESS} 才会使 {@link #accepted()} 返回
 * {@code true}。排队、部分成功和失败都必须通过 {@link #status()}、
 * {@link #code()} 及其他字段进一步判断。</p>
 *
 * @param status 操作状态
 * @param code 面向调用方的稳定反馈代码
 * @param requestId 本次请求的标识；无请求标识的失败结果使用全零 UUID
 * @param target 操作目标的稳定描述
 * @param affectedCount 本次操作影响的对象数量
 * @param parameters 与结果相关的附加参数
 * @param generation 操作完成后对应的策略代数
 */
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

    /**
     * 判断操作是否已被当前 API 层成功接受。
     *
     * @return 仅当 {@link #status()} 为 {@link FilterOperationStatus#SUCCESS} 时返回 {@code true}
     */
    public boolean accepted() {
        return status == FilterOperationStatus.SUCCESS;
    }

    /**
     * 创建一个成功结果。
     *
     * @param requestId 本次请求的标识
     * @param target 操作目标
     * @param affectedCount 影响的对象数量
     * @param generation 操作完成后的策略代数
     * @return 成功结果
     */
    public static OperationResult success(UUID requestId, String target, int affectedCount, long generation) {
        return new OperationResult(FilterOperationStatus.SUCCESS, FeedbackCode.OK, requestId,
                target, affectedCount, Map.of(), generation);
    }

    /**
     * 创建一个失败结果。
     *
     * @param status 具体失败状态；传入 {@code null} 时由构造器归一化为 {@code FAILED}
     * @param code 稳定反馈代码；传入 {@code null} 时由构造器归一化为 {@code FAILED}
     * @param target 操作目标
     * @param generation 失败发生时观察到的策略代数
     * @return 失败结果
     */
    public static OperationResult failure(FilterOperationStatus status, FeedbackCode code,
                                          String target, long generation) {
        return new OperationResult(status, code, new UUID(0L, 0L), target, 0, Map.of(), generation);
    }
}
