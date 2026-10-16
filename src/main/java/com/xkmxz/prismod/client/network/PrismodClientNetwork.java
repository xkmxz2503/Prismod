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

/** 协议 3 客户端适配器；请求 ID、策略 ID 与客户端资源 ID 分别管理，strength 使用百分制。 */
public final class PrismodClientNetwork {
    private static final PrismodClientNetwork INSTANCE = new PrismodClientNetwork(FilterClientApi::submit);
    private final Map<UUID, OverrideCreation> overrides = new HashMap<>();
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
                OverrideCreation previous = overrides.get(message.policyId());
                if (previous != null && previous.requestId().equals(message.requestId())) return;
                if (message.filter() != null) {
                    if (previous != null) release(previous.creation());
                    overrides.put(message.policyId(), new OverrideCreation(message.requestId(), submit.apply(
                        new FilterRequest.Override(message.owner(), message.filter(),
                                message.strength(), message.priority()))));
                }
            }
            case CLEAR_OVERRIDE -> {
                OverrideCreation previous = overrides.remove(message.policyId());
                if (previous != null) release(previous.creation());
            }
            case CLEAR_SELECTION -> submit.apply(new FilterRequest.ClearSelection());
            case CLEAR_ALL_OVERRIDES -> clearOverrides();
        }
    }

    private void release(CompletionStage<FilterReceipt> creation) {
        // 捕获本次创建结果，不回查映射；旧连接延迟结果只能释放自身资源。
        if (creation != null) creation.thenAccept(receipt -> receipt.resourceIds().forEach(id ->
                submit.apply(new FilterRequest.Release(id))));
    }

    private void clearOverrides() {
        List<OverrideCreation> creations = List.copyOf(overrides.values());
        overrides.clear();
        creations.forEach(creation -> release(creation.creation()));
    }

    public static void resetSession() {
        INSTANCE.reset();
    }

    synchronized void reset() {
        clearOverrides();
        submit.apply(new FilterRequest.ClearSelection());
    }

    private record OverrideCreation(UUID requestId, CompletionStage<FilterReceipt> creation) {
    }
}
