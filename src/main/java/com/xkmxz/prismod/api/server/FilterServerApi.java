package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.api.server.query.request.ServerFilterQuery;
import com.xkmxz.prismod.api.server.submit.request.ServerFilterRequest;
import com.xkmxz.prismod.api.server.submit.result.OperationResult;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import com.xkmxz.prismod.server.application.ServerFilterApplication;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.CompletionStage;

/** 服务端唯一业务门面：类型化提交与只读查询，不加载客户端实现。 */
public final class FilterServerApi {
    /** 与网络实现共享的频道契约。 */
    public static final ResourceLocation NETWORK_CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(
            PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH);
    /** 与网络实现共享的协议版本。 */
    public static final String PROTOCOL_VERSION = PolicyTransport.PROTOCOL_VERSION;
    private FilterServerApi() { }

    /** 调度到服务端线程，状态发布后交付一次最终回执。不得在服务端线程阻塞等待。 */
    public static CompletionStage<OperationResult> submit(ServerFilterRequest request) {
        return ServerFilterApplication.submit(request);
    }
    /** 返回最近发布的不可变服务端视图，不等待排队请求，也不查询远端客户端。 */
    public static <R> R query(ServerFilterQuery<R> request) {
        return ServerFilterApplication.query(request);
    }
}
