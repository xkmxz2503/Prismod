package com.xkmxz.prismod.api.server.submit.request;

import com.xkmxz.prismod.api.server.submit.model.ServerFilterTarget;

/** 服务端类型化业务请求；执行时统一校验，业务失败通过最终回执表达。 */
public sealed interface ServerFilterRequest {
    /** 更新目标范围的选择；不写客户端玩家配置。 */
    record Select(ServerFilterTarget target, FilterSelectionRequest selection) implements ServerFilterRequest { }
    /** 更新目标范围内同 owner 的覆盖，策略 UUID 保持不变。 */
    record Override(ServerFilterTarget target, FilterOverrideRequest override) implements ServerFilterRequest { }
    /** 清理指定范围内的 owner；重复清理成功且影响数量为零。 */
    record ClearOverride(ServerFilterTarget target, String ownerId) implements ServerFilterRequest { }
    /** 清理全部全局和玩家覆盖。 */
    record ClearAllOverrides() implements ServerFilterRequest { }
    /** 清理全部全局和玩家选择。 */
    record ClearSelection() implements ServerFilterRequest { }
}
