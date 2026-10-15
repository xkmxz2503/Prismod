package com.xkmxz.prismod.api.common.query.state.filter;

/**
 * 说明最终生效滤镜为何不同于请求或选择的滤镜。
 *
 * <p>{@link #NONE} 表示没有发生回退。其余值用于状态展示、日志和诊断；它们不应
 * 被调用方当作异常消息直接展示，具体文字应由调用方根据语言环境生成。</p>
 */
public enum FilterFallbackReason {
    /** 没有发生回退。 */
    NONE,
    /** 滤镜功能当前被禁用。 */
    DISABLED,
    /** 请求的滤镜资源不可用。 */
    UNAVAILABLE,
    /** 当前渲染管线无法使用请求的滤镜。 */
    RENDER_UNAVAILABLE,
    /** 当前覆盖请求无效，未能成为最终生效策略。 */
    INVALID_OVERRIDE
}
