package com.xkmxz.prismod.api.server;

import net.minecraft.resources.ResourceLocation;

/**
 * Prismod 服务端框架的不可变状态快照。
 *
 * <p>该对象只反映服务端 API 当前可观察到的基础状态，适合用于启动检查、日志和
 * 命令诊断。它不表示任何具体玩家已经收到策略，也不表示客户端已经完成渲染。</p>
 *
 * @param initialized 服务端应用层是否已经完成初始化
 * @param networkChannel 服务端策略网络使用的频道标识
 * @param protocolVersion 当前策略网络协议版本
 */
public record ServerStatus(boolean initialized, ResourceLocation networkChannel, String protocolVersion) {
}
