package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.client.operation.FilterBatchOperation;
import com.xkmxz.prismod.api.common.state.operation.FilterOperationStatus;

import java.util.List;
import java.util.Objects;

/** Immutable result containing handles and per-item state for a client batch. */
public record FilterBatchResult<T>(List<T> handles,
                                   FilterBatchOperation operation,
                                   List<FilterBatchItem<T>> items) {
    public FilterBatchResult {
        handles = List.copyOf(handles == null ? List.of() : handles);
        operation = Objects.requireNonNull(operation, "operation");
        items = List.copyOf(items == null ? List.of() : items);
    }

    public FilterOperationStatus status() {
        return operation.status();
    }
}
