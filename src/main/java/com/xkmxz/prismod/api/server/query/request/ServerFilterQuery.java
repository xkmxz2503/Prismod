package com.xkmxz.prismod.api.server.query.request;

import com.xkmxz.prismod.api.server.query.view.ServerFilterStatus;
import com.xkmxz.prismod.api.server.query.view.ServerStatus;

/** 只读取服务端最近发布的不可变状态，不查询远端客户端。 */
public sealed interface ServerFilterQuery<R> {
    /** 服务端应用层及协议状态。 */
    record Status() implements ServerFilterQuery<ServerStatus> { }
    /** 服务端保存的全局选择、策略数量和代数。 */
    record FilterStatus() implements ServerFilterQuery<ServerFilterStatus> { }
}
