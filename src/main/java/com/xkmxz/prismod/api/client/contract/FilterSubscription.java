package com.xkmxz.prismod.api.client.contract;

/** Removable client API subscription. */
public interface FilterSubscription extends AutoCloseable {
    @Override
    void close();
}
