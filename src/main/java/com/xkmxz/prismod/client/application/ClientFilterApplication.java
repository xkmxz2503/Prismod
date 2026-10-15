package com.xkmxz.prismod.client.application;

import com.xkmxz.prismod.api.client.contract.FilterReceipt;
import com.xkmxz.prismod.api.client.query.FilterQuery;
import com.xkmxz.prismod.api.client.request.FilterRequest;
import net.minecraft.client.Minecraft;
import java.util.concurrent.CompletionStage;

/** 客户端请求应用层；内部实现不属于外部 API。 */
public final class ClientFilterApplication {
    private ClientFilterApplication() { }

    private static final class Holder {
        private static final ClientFilterDispatcher INSTANCE = new ClientFilterDispatcher(work -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isSameThread()) work.run();
            else minecraft.execute(work);
        }, new MinecraftFilterBackend());
    }

    public static CompletionStage<FilterReceipt> submit(FilterRequest request) {
        return Holder.INSTANCE.submit(request);
    }

    public static <R> R query(FilterQuery<R> request) {
        return Holder.INSTANCE.query(request);
    }
}
