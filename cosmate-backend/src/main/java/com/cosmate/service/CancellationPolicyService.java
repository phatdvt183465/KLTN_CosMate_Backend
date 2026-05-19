package com.cosmate.service;

import com.cosmate.entity.ProviderCancellationPolicy;

import java.util.List;

public interface CancellationPolicyService {
    List<ProviderCancellationPolicy> listByProvider(Integer providerId);
    ProviderCancellationPolicy create(ProviderCancellationPolicy policy);
    ProviderCancellationPolicy update(ProviderCancellationPolicy policy);
    void delete(Integer id);
    void createDefaultsForProvider(Integer providerId);
    /**
     * Create default policies for all providers that currently have no policies.
     * Returns the number of providers seeded.
     */
    int createDefaultsForProvidersMissingPolicies();
}

