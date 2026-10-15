package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.server.application.ServerFilterApplication;
import com.xkmxz.prismod.api.server.query.view.ServerStatus;
import com.xkmxz.prismod.api.server.query.view.ServerFilterStatus;

import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import com.xkmxz.prismod.api.server.submit.result.OperationResult;
import com.xkmxz.prismod.api.server.submit.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.server.submit.request.FilterSelectionRequest;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prismod 服务端公开 API。
 *
 * <p>服务端模组或管理命令通过此门面提交滤镜策略。此 API 负责保存服务端拥有的
 * 选择与覆盖，并通过 {@link PolicyTransport} 将策略发送给客户端；它不直接渲染
 * 世界画面，也不返回客户端已经完成渲染的回执。</p>
 *
 * <p>方法按目标范围分为两组：{@code broadcast*} 作用于当前连接的所有玩家，
 * {@code send*} 只作用于指定玩家。清理方法按同样的范围撤销已保存的策略。</p>
 */
public final class FilterServerApi {
    /** 服务端与客户端之间使用的网络频道标识。 */
    public static final ResourceLocation NETWORK_CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(
            PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH);
    /** 当前策略网络协议版本。 */
    public static final String PROTOCOL_VERSION = PolicyTransport.PROTOCOL_VERSION;

    /** 不允许实例化静态 API 门面。 */
    private FilterServerApi() {
    }

    /**
     * 返回 Prismod 服务端框架的基础状态快照。
     *
     * @return 初始化状态、网络频道和协议版本
     */
    public static ServerStatus status() {
        return ServerFilterApplication.status();
    }

    /**
     * 返回服务端当前保存的滤镜策略快照。
     *
     * @return 全局选择、强度、覆盖数量、玩家策略数量和代数计数
     */
    public static ServerFilterStatus filterStatus() {
        return ServerFilterApplication.filterStatus();
    }

    /**
     * 向所有当前玩家广播全局滤镜选择。
     *
     * @param request 要保存并广播的滤镜选择请求
     * @return 服务端接受或拒绝本次策略操作的结果
     */
    public static OperationResult broadcastSelection(FilterSelectionRequest request) { return ServerFilterApplication.broadcastSelection(request); }

    /**
     * 向指定玩家发送滤镜选择。
     *
     * @param player 目标玩家
     * @param request 要保存并发送的滤镜选择请求
     * @return 服务端接受或拒绝本次策略操作的结果
     */
    public static OperationResult sendSelection(ServerPlayer player, FilterSelectionRequest request) { return ServerFilterApplication.sendSelection(player, request); }

    /**
     * 向所有当前玩家广播一个由 {@code ownerId} 标识的覆盖策略。
     * 同一 owner 再次提交时会更新该 owner 的策略。
     *
     * @param request 要保存并广播的覆盖请求
     * @return 服务端接受或拒绝本次策略操作的结果
     */
    public static OperationResult broadcastOverride(FilterOverrideRequest request) { return ServerFilterApplication.broadcastOverride(request); }

    /**
     * 向指定玩家发送一个由 {@code ownerId} 标识的覆盖策略。
     *
     * @param player 目标玩家
     * @param request 要保存并发送的覆盖请求
     * @return 服务端接受或拒绝本次策略操作的结果
     */
    public static OperationResult sendOverride(ServerPlayer player, FilterOverrideRequest request) { return ServerFilterApplication.sendOverride(player, request); }

    /**
     * 清理所有玩家共享的指定 owner 覆盖策略。
     *
     * @param ownerId 要清理的策略所有者标识
     * @return 服务端接受或拒绝清理操作的结果
     */
    public static OperationResult clearBroadcastOverride(String ownerId) { return ServerFilterApplication.clearBroadcastOverride(ownerId); }

    /**
     * 清理指定玩家的指定 owner 覆盖策略。
     *
     * @param player 目标玩家
     * @param ownerId 要清理的策略所有者标识
     * @return 服务端接受或拒绝清理操作的结果
     */
    public static OperationResult clearPlayerOverride(ServerPlayer player, String ownerId) { return ServerFilterApplication.clearPlayerOverride(player, ownerId); }

    /**
     * 清理所有全局和玩家覆盖策略。
     *
     * @return 服务端接受或拒绝清理操作的结果
     */
    public static OperationResult clearAllOverrides() { return ServerFilterApplication.clearAllOverrides(); }

    /**
     * 清理全局选择和所有玩家选择。
     *
     * @return 服务端接受或拒绝清理操作的结果
     */
    public static OperationResult clearSelection() { return ServerFilterApplication.clearSelection(); }
}
