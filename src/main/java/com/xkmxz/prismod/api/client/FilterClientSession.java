package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.FilterBatchResult;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import com.xkmxz.prismod.api.client.contract.FilterRegistrationRequest;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;

import java.util.List;

/**
 * Owner-scoped lifecycle for custom registrations and overrides.
 *
 * <p>Batch methods preserve input order and use best-effort semantics. Closing a
 * session is idempotent and only releases handles created through that session;
 * queued work is allowed to finish and then closes its resulting handle.</p>
 */
public interface FilterClientSession extends AutoCloseable {
    /** @return owner identifier shared by registrations and overrides in this session */
    String ownerId();

    /**
     * Registers all requests in input order using best-effort semantics.
     *
     * @param requests registration requests; null entries become failed items
     * @return handles, per-item results, and aggregate batch operation
     */
    FilterBatchResult<FilterRegistration> registerAll(List<FilterRegistrationRequest> requests);

    /**
     * Creates all overrides in input order using best-effort semantics.
     *
     * @param requests override requests; null entries become failed items
     * @return handles, per-item results, and aggregate batch operation
     */
    FilterBatchResult<FilterOverride> createOverrides(List<FilterOverrideRequest> requests);

    /** @return operation describing cleanup of handles created through this session */
    FilterOperation clear();

    /** @return whether this session has been closed */
    boolean closed();

    /** Closes the session; repeated calls have no effect. */
    @Override void close();
}
