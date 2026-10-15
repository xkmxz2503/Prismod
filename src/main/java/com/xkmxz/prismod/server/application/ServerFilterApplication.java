package com.xkmxz.prismod.server.application;

import com.xkmxz.prismod.api.server.query.request.ServerFilterQuery;
import com.xkmxz.prismod.api.server.submit.request.ServerFilterRequest;
import com.xkmxz.prismod.api.server.submit.result.OperationResult;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;

/** 服务端 API、命令与生命周期共用的应用层，不加载客户端类。 */
public final class ServerFilterApplication {
    private static volatile PolicyTransport transport;
    private static final ServerFilterDispatcher DISPATCHER = new ServerFilterDispatcher(new ServerFilterRuntime() {
        @Override public void execute(Runnable task) {
            MinecraftServer server = currentServer();
            if (server == null || server.isStopped()) throw new RejectedExecutionException("server unavailable");
            if (server.isSameThread()) task.run();
            else server.execute(task);
        }
        @Override public boolean available() {
            MinecraftServer server = currentServer();
            return transport != null && server != null && !server.isStopped();
        }
        @Override public List<UUID> onlinePlayers() {
            MinecraftServer server = currentServer();
            return server == null ? List.of() : server.getPlayerList().getPlayers().stream()
                    .map(ServerPlayer::getUUID).toList();
        }
        @Override public void send(UUID player, PolicyMessage message) {
            PolicyTransport port = Objects.requireNonNull(transport, "transport");
            if (player == null) port.broadcast(message);
            else {
                MinecraftServer server = Objects.requireNonNull(currentServer(), "server");
                ServerPlayer target = server.getPlayerList().getPlayer(player);
                if (target != null) port.send(target, message);
            }
        }
    });

    private ServerFilterApplication() { }

    /** 由启动入口装配传输端口。 */
    public static void initialize(PolicyTransport policyTransport) {
        transport = Objects.requireNonNull(policyTransport);
        DISPATCHER.initialize();
    }
    public static CompletionStage<OperationResult> submit(ServerFilterRequest request) {
        return DISPATCHER.submit(request);
    }
    public static <R> R query(ServerFilterQuery<R> request) { return DISPATCHER.query(request); }

    /** 同一进程重新进入存档时重新启用队列，策略不跨服务端会话保留。 */
    public static void onServerStarting(ServerStartingEvent event) {
        if (transport != null) DISPATCHER.initialize();
    }
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DISPATCHER.replay(player.getUUID());
    }
    public static void onServerStopped(ServerStoppedEvent event) { DISPATCHER.stop(); }

    private static MinecraftServer currentServer() { return ServerLifecycleHooks.getCurrentServer(); }
}
