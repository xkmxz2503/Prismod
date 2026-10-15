package com.xkmxz.prismod.server.application;

import com.xkmxz.prismod.network.contract.PolicyMessage;
import java.util.List;
import java.util.UUID;

/** 服务端执行环境；业务层不持有世界或玩家实例。 */
interface ServerFilterRuntime {
    void execute(Runnable task);
    boolean available();
    List<UUID> onlinePlayers();
    /** player 为空时广播。 */
    void send(UUID player, PolicyMessage message);
}
