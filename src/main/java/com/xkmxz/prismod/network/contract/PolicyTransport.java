package com.xkmxz.prismod.network.contract;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Stable transport port used by server application services. */
public interface PolicyTransport {
    String CHANNEL_NAMESPACE = "prismod";
    String CHANNEL_PATH = "policy";
    String PROTOCOL_VERSION = "1";

    void registerClientSink(Consumer<PolicyMessage> sink);

    void broadcast(PolicyMessage message);

    void send(ServerPlayer player, PolicyMessage message);
}
