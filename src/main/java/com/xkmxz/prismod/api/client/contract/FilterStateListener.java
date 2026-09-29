package com.xkmxz.prismod.api.client.contract;

@FunctionalInterface
public interface FilterStateListener {
    void onChanged(FilterSnapshot snapshot);
}
