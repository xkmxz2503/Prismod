package com.xkmxz.prismod.api.client;

@FunctionalInterface
public interface FilterStateListener {
    void onChanged(FilterSnapshot snapshot);
}
