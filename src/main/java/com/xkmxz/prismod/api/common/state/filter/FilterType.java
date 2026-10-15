package com.xkmxz.prismod.api.common.state.filter;

/**
 * 公开滤镜描述中的来源类型。
 *
 * <p>该枚举用于说明滤镜资源如何被解释，不改变滤镜逻辑 ID 的身份。调用方应
 * 将未知或未来扩展的类型当作不可依赖具体实现的描述处理。</p>
 */
public enum FilterType {
    /** 基于 Minecraft PostChain 的滤镜。 */
    POST_CHAIN,
    /** 基于三维查找表的滤镜。 */
    LUT3D,
    /** 类型未知或当前版本无法识别。 */
    UNKNOWN
}
