package com.xkmxz.prismod.api.client.contract;

/**
 * Removable client API subscription.
 *
 * <p>Closing stops future callbacks. Closing is idempotent and does not replay
 * or synthesize a final event.</p>
 */
public interface FilterSubscription extends AutoCloseable {
    /** Stops future callbacks for this subscription. */
    @Override
    void close();
}
