package com.xkmxz.prismod.api.common.state.filter;

/**
 * 对外暴露的稳定失败分类，不泄露底层实现异常类型。
 *
 * <p>这些值适合用于筛选、日志和诊断。需要向用户展示文字时，应由调用方根据
 * 语言环境将分类转换为本地化消息，并结合 {@code failureDetail} 获取补充信息。</p>
 */
public enum FilterFailureReason {
    /** 没有失败。 */
    NONE,
    /** 注册或描述参数无效。 */
    INVALID_ARGUMENT,
    /** 滤镜 ID 与已有注册项冲突。 */
    ID_CONFLICT,
    /** 需要的资源不存在。 */
    RESOURCE_MISSING,
    /** 资源存在但格式或内容无效。 */
    RESOURCE_INVALID,
    /** 渲染阶段执行失败。 */
    RENDER_FAILED,
    /** 未归类的失败。 */
    UNKNOWN
}
