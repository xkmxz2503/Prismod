package com.xkmxz.prismod.api.common.result;

/**
 * API 操作返回的稳定、与语言无关的反馈代码。
 *
 * <p>反馈代码适合用于日志、命令反馈和跨网络适配；自然语言消息应由调用方根据
 * 自己的界面或语言环境生成。更细的执行状态仍通过 {@code OperationResult.status()}
 * 提供。</p>
 */
public enum FeedbackCode {
    /** 操作已成功接受。 */
    OK,
    /** 请求参数不符合 API 契约。 */
    INVALID_ARGUMENT,
    /** 请求目标或要清理的对象不存在。 */
    NOT_FOUND,
    /** 当前运行环境暂时无法执行操作。 */
    UNAVAILABLE,
    /** 操作引用的句柄或会话已经关闭。 */
    ALREADY_CLOSED,
    /** 发生未归类的执行失败。 */
    FAILED
}
