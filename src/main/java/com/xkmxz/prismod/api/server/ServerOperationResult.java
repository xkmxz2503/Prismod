package com.xkmxz.prismod.api.server;

import com.xkmxz.prismod.api.common.state.FilterOperationStatus;

import java.util.UUID;

/** Immutable result returned by server-side filter dispatch operations. */
public record ServerOperationResult(
        FilterOperationStatus status,
        UUID requestId,
        String target,
        String detail,
        float strength,
        long generation
) {
    public boolean accepted() {
        return status == FilterOperationStatus.SUCCESS;
    }

    public ServerOperationResult {
        status = status == null ? FilterOperationStatus.FAILED : status;
        target = target == null ? "" : target;
        detail = detail == null ? "" : detail;
        strength = Float.isFinite(strength) ? Math.max(0.0F, Math.min(1.0F, strength)) : 0.0F;
    }
}
