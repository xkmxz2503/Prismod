package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/**
 * 当前资源的不可变视图；ACTIVE 表示参与管理，不表示覆盖赢得优先级。
 * Session 与订阅没有 filter；独立资源没有 sessionId。
 */
public record FilterResourceView(UUID resourceId, Kind kind, String ownerId, UUID sessionId,
                                 ResourceLocation filter, int priority, State state,
                                 FilterFailureReason failureReason, String failureDetail) {
    public enum Kind { REGISTRATION, OVERRIDE, SESSION, SUBSCRIPTION }
    public enum State { ACTIVE, FAILED }
    public FilterResourceView {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(failureReason, "failureReason");
        failureDetail = failureDetail == null ? "" : failureDetail;
    }
}
