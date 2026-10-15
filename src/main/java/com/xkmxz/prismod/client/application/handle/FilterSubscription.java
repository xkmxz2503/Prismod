package com.xkmxz.prismod.client.application.handle;

/** 内部监听注销句柄。 */
@FunctionalInterface
public interface FilterSubscription extends AutoCloseable {
    @Override void close();
}
