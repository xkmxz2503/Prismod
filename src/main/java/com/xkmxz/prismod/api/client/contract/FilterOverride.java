package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.common.state.lifecycle.FilterOverrideState;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import net.minecraft.resources.ResourceLocation;

/** Lifecycle handle for one owner-scoped runtime filter override. */
public interface FilterOverride extends AutoCloseable {
    /** @return logical {@code namespace:path} identity selected by this override */
    ResourceLocation id();

    /** @return normalized owner identifier that created this override */
    String ownerId();

    /** @return priority used when choosing among active overrides */
    int priority();

    /** @return current override lifecycle state */
    FilterOverrideState state();

    /** @return whether this override currently participates in selection */
    boolean isActive();

    /**
     * Returns the operation that created this handle.
     *
     * @return observable operation; completed compatibility handles return a
     *         successful zero-impact operation
     */
    default FilterOperation operation() {
        return FilterOperation.completedOperation();
    }

    /** Closes this override; repeated calls have no effect. */
    @Override
    void close();
}
