package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;

/** Immutable view of one item in a client batch. */
public record FilterBatchItem<T>(int index, T handle, FilterOperation operation) {
    public FilterOperationStatus status() {
        return operation.status();
    }
}
