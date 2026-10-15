package com.xkmxz.prismod.api.client;

import com.xkmxz.prismod.api.client.contract.FilterBatchResult;
import com.xkmxz.prismod.api.client.contract.FilterOverride;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import com.xkmxz.prismod.api.client.contract.FilterRegistrationRequest;
import com.xkmxz.prismod.api.client.operation.FilterOperation;
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;

import java.util.List;

/** Owner-scoped lifecycle for custom registrations and overrides. */
public interface FilterClientSession extends AutoCloseable {
    String ownerId();
    FilterBatchResult<FilterRegistration> registerAll(List<FilterRegistrationRequest> requests);
    FilterBatchResult<FilterOverride> createOverrides(List<FilterOverrideRequest> requests);
    FilterOperation clear();
    boolean closed();
    @Override void close();
}
