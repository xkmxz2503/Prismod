package com.xkmxz.prismod.api.client.operation;

import com.xkmxz.prismod.api.common.state.FilterOperationStatus;

import java.util.UUID;

/** Internal mutable implementation exposed through the immutable FilterOperation contract. */
final class MutableFilterOperation implements FilterOperation {
    private final UUID id;
    private volatile FilterOperationStatus status = FilterOperationStatus.QUEUED;
    private volatile int affectedCount;
    private volatile String detail = "";

    MutableFilterOperation(UUID id) {
        this.id = id == null ? UUID.randomUUID() : id;
    }

    void complete(FilterOperationStatus status, int affectedCount, String detail) {
        this.status = status == null ? FilterOperationStatus.FAILED : status;
        this.affectedCount = Math.max(0, affectedCount);
        this.detail = detail == null ? "" : detail;
    }

    @Override public UUID id() { return id; }
    @Override public FilterOperationStatus status() { return status; }
    @Override public int affectedCount() { return affectedCount; }
    @Override public String detail() { return detail; }
    @Override public boolean completed() { return status != FilterOperationStatus.QUEUED; }
}
