package com.xkmxz.prismod.api.server.submit.model;

import java.util.UUID;

/** 策略目标；只保存身份，不保存玩家或世界对象。 */
public sealed interface ServerFilterTarget {
    /** 全局策略，应用于当前玩家并在登录时补发。 */
    record Broadcast() implements ServerFilterTarget { }
    /** 指定玩家；提交执行时必须在线。 */
    record Player(UUID playerId) implements ServerFilterTarget { }
}
