package com.xkmxz.prismod.client.network;

import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.submit.result.FilterReceipt;
import com.xkmxz.prismod.api.client.submit.request.FilterRequest;
import com.xkmxz.prismod.network.contract.PolicyMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** 冻结协议的客户端适配器；网络请求 ID 与 API 资源 ID 分别管理。 */
public final class PrismodClientNetwork {
    private static final PrismodClientNetwork INSTANCE = new PrismodClientNetwork(FilterClientApi::submit);
    private final Map<UUID, CompletionStage<FilterReceipt>> overrides = new HashMap<>();
    private final Function<FilterRequest, CompletionStage<FilterReceipt>> submit;

    /** 包内注入业务入口，测试无需加载 Minecraft 客户端运行环境。 */
    PrismodClientNetwork(Function<FilterRequest, CompletionStage<FilterReceipt>> submit) {
        this.submit = Objects.requireNonNull(submit, "submit");
    }

    public static void handle(PolicyMessage message) {
        INSTANCE.accept(message);
    }

    synchronized void accept(PolicyMessage message) {
        switch (message.operation()) {
            case SELECT -> {
                if (message.filter() != null) submit.apply(
                        new FilterRequest.Select(message.filter(), message.strength()));
            }
            case OVERRIDE -> {
                if (overrides.containsKey(message.requestId())) return;
                if (message.filter() != null) overrides.put(message.requestId(), submit.apply(
                        new FilterRequest.Override(message.owner(), message.filter(),
                                message.strength(), message.priority())));
            }
            case CLEAR_OVERRIDE -> release(overrides.remove(message.requestId()));
            case CLEAR_SELECTION -> submit.apply(new FilterRequest.ClearSelection());
            case CLEAR_ALL_OVERRIDES -> clearOverrides();
        }
    }

    private void release(CompletionStage<FilterReceipt> creation) {
        // 捕获本次创建结果，不再按网络请求 ID 查找，避免误清理后来复用该 ID 的覆盖。
        if (creation != null) creation.thenAccept(receipt -> receipt.resourceIds().forEach(id ->
                submit.apply(new FilterRequest.Release(id))));
    }

    private void clearOverrides() {
        List<CompletionStage<FilterReceipt>> creations = List.copyOf(overrides.values());
        overrides.clear();
        creations.forEach(this::release);
    }

    public static void resetSession() {
        INSTANCE.reset();
    }

    synchronized void reset() {
        clearOverrides();
        submit.apply(new FilterRequest.ClearSelection());
    }
}
