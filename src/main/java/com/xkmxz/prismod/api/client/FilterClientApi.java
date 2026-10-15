package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.FilterReceipt;
import com.xkmxz.prismod.api.client.query.FilterQuery;
import com.xkmxz.prismod.api.client.request.FilterRequest;
import com.xkmxz.prismod.client.application.ClientFilterApplication;
import java.util.concurrent.CompletionStage;

/** 客户端唯一业务门面：提交请求与只读查询。专用服务器不得加载。 */
public final class FilterClientApi {
    private FilterClientApi() { }

    /** 自动调度到客户端线程，完成后交付一次性最终回执。 */
    public static CompletionStage<FilterReceipt> submit(FilterRequest request) {
        return ClientFilterApplication.submit(request);
    }

    /** 读取最近发布的不可变视图，不等待排队请求，不产生业务副作用。 */
    public static <R> R query(FilterQuery<R> request) {
        return ClientFilterApplication.query(request);
    }
}
