package com.xkmxz.prismod.network.contract;

import net.minecraft.server.level.ServerPlayer;

/** Stable server-side transport port used by application services. */
public interface PolicyTransport {
    String CHANNEL_NAMESPACE = "prismod";
    String CHANNEL_PATH = "policy";
    String PROTOCOL_VERSION = "2";

    /** Sends a policy message to every connected client. */
    void broadcast(PolicyMessage message);

    /** Sends a policy message to one connected player. */
    void send(ServerPlayer player, PolicyMessage message);
}
