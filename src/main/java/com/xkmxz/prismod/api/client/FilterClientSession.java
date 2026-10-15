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
    String ownerId();
    FilterBatchResult<FilterRegistration> registerAll(List<FilterRegistrationRequest> requests);
    FilterBatchResult<FilterOverride> createOverrides(List<FilterOverrideRequest> requests);
    FilterOperation clear();
    boolean closed();
    @Override void close();
}
