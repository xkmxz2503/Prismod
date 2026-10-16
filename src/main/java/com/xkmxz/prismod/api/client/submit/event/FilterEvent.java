package com.xkmxz.prismod.api.client.submit.event;

import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.submit.result.FilterReceipt;
import com.xkmxz.prismod.api.client.query.view.FilterDescriptor;

import java.util.List;
import java.util.Objects;

/** Immutable notification for client filter state, registry, availability, or operation changes. */
public record FilterEvent(FilterEventType type, FilterSnapshot snapshot,
                          List<FilterDescriptor> filters, FilterReceipt receipt) {
    public FilterEvent {
        type = Objects.requireNonNull(type, "type");
        filters = filters == null ? List.of() : List.copyOf(filters);
    }

    public static FilterEvent snapshotChanged(FilterSnapshot snapshot) {
        return new FilterEvent(FilterEventType.SNAPSHOT_CHANGED, snapshot, List.of(), null);
    }

    public static FilterEvent registryChanged(List<FilterDescriptor> filters) {
        return new FilterEvent(FilterEventType.REGISTRY_CHANGED, null, filters, null);
    }

    public static FilterEvent availabilityChanged(List<FilterDescriptor> filters) {
        return new FilterEvent(FilterEventType.AVAILABILITY_CHANGED, null, filters, null);
    }

    public static FilterEvent operationCompleted(FilterReceipt receipt) {
        return new FilterEvent(FilterEventType.OPERATION_COMPLETED, null, List.of(),
                Objects.requireNonNull(receipt, "receipt"));
    }

    public static FilterEvent batchOperationCompleted(FilterReceipt receipt) {
        return new FilterEvent(FilterEventType.BATCH_OPERATION_COMPLETED, null, List.of(),
                Objects.requireNonNull(receipt, "receipt"));
    }
}
