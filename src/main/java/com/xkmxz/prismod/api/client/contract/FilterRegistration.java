package com.xkmxz.prismod.api.client.contract;

import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import net.minecraft.resources.ResourceLocation;

/**
 * Lifecycle handle for one owner-scoped custom filter registration.
 *
 * <p>The handle remains observable after a failed or queued registration. Call
 * {@link #close()} when the owning feature is disabled; closing is idempotent.</p>
 */
public interface FilterRegistration extends AutoCloseable {
    /** @return logical {@code namespace:path} identity of the registered filter */
    ResourceLocation id();

    /** @return normalized owner identifier that created this registration */
    String ownerId();

    /** @return current registration lifecycle state */
    FilterRegistrationState state();

    /** @return whether this registration currently contributes an active filter */
    default boolean isActive() {
        return state() == FilterRegistrationState.ACTIVE;
    }

    /** @return stable failure category, or {@link FilterFailureReason#NONE} */
    default FilterFailureReason failureReason() {
        return FilterFailureReason.NONE;
    }

    /** @return short diagnostic detail for {@link #failureReason()} */
    default String failureDetail() {
        return "";
    }

    /**
     * Returns the operation that created this handle.
     *
     * @return observable operation; completed compatibility handles return a
     *         successful zero-impact operation
     */
    default FilterOperation operation() {
        return FilterOperation.completedOperation();
    }

    /** Closes this registration; repeated calls have no effect. */
    @Override
    void close();
}
