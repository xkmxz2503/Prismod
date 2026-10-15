package com.xkmxz.prismod.api.common.submit.state.operation;

/**
 * 客户端或服务端滤镜操作的结果状态。
 *
 * <p>该枚举描述当前 API 层观察到的操作结果，不直接描述客户端最终渲染画面。
 * {@link #QUEUED} 表示操作已提交但尚未完成；终态通常为 {@link #SUCCESS} 或
 * {@link #FAILED}，具体失败原因由配套的反馈代码提供。</p>
 */
public enum FilterOperationStatus {
    /** 操作已完成并成功生效。 */
    SUCCESS,
    /** 操作已提交，仍在等待调度或执行。 */
    QUEUED,
    /** 请求参数不符合当前 API 契约。 */
    INVALID_ARGUMENT,
    /** 请求目标或要清理的对象不存在。 */
    NOT_FOUND,
    /** 当前运行环境暂时无法执行操作。 */
    UNAVAILABLE,
    /** 操作对应的句柄或会话已经关闭。 */
    CLOSED,
    /** 批量操作只对部分目标成功。 */
    PARTIAL,
    /** 操作执行失败，且无法归入更具体的状态。 */
    FAILED
}
