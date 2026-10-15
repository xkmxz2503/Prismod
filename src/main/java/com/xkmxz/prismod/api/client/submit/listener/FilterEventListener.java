package com.xkmxz.prismod.api.client.submit.listener;

import com.xkmxz.prismod.api.client.submit.event.FilterEvent;

@FunctionalInterface
public interface FilterEventListener {
    void onEvent(FilterEvent event);
}
