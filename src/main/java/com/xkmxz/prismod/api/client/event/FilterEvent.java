package com.xkmxz.prismod.api.client.event;

import com.xkmxz.prismod.api.client.FilterSnapshot;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;

import java.util.List;
import java.util.Objects;

/** Immutable notification for client filter state, registry, availability, or operation changes. */
public record FilterEvent(FilterEventType type, FilterSnapshot snapshot,
                          List<FilterDescriptor> filters, FilterOperation operation) {
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

    public static FilterEvent operationCompleted(FilterOperation operation) {
        return new FilterEvent(FilterEventType.OPERATION_COMPLETED, null, List.of(),
                Objects.requireNonNull(operation, "operation"));
    }
}
