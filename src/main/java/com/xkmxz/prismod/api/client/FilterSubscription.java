package com.xkmxz.prismod.api.client;

/** Removable client API subscription. */
public interface FilterSubscription extends AutoCloseable {
    @Override
    void close();
}
