package com.cosmate.repository;

import com.cosmate.entity.ProviderCancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderCancellationPolicyRepository extends JpaRepository<ProviderCancellationPolicy, Integer> {
    List<ProviderCancellationPolicy> findByProviderIdOrderByMinHoursBeforeDesc(Integer providerId);
    List<ProviderCancellationPolicy> findByProviderId(Integer providerId);
}

