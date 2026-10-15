package com.xkmxz.prismod.api.client.submit.listener;

import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;

@FunctionalInterface
public interface FilterStateListener {
    void onChanged(FilterSnapshot snapshot);
}
