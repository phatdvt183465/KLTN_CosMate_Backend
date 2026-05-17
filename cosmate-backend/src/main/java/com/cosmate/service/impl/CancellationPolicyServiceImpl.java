package com.cosmate.service.impl;

import com.cosmate.entity.Provider;
import com.cosmate.entity.ProviderCancellationPolicy;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.ProviderCancellationPolicyRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.CancellationPolicyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CancellationPolicyServiceImpl implements CancellationPolicyService {

    private final ProviderCancellationPolicyRepository policyRepository;
    private final ProviderRepository providerRepository;

    @Override
    public List<ProviderCancellationPolicy> listByProvider(Integer providerId) {
        return policyRepository.findByProviderIdOrderByMinHoursBeforeDesc(providerId);
    }

    @Override
    @Transactional
    public ProviderCancellationPolicy create(ProviderCancellationPolicy policy) {
        if (policy.getProvider() == null || policy.getProvider().getId() == null) throw new AppException(ErrorCode.INVALID_KEY);
        return policyRepository.save(policy);
    }

    @Override
    @Transactional
    public ProviderCancellationPolicy update(ProviderCancellationPolicy policy) {
        if (policy.getId() == null) throw new AppException(ErrorCode.INVALID_KEY);
        ProviderCancellationPolicy existing = policyRepository.findById(policy.getId()).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        existing.setMinHoursBefore(policy.getMinHoursBefore());
        existing.setMaxHoursBefore(policy.getMaxHoursBefore());
        existing.setPenaltyType(policy.getPenaltyType());
        existing.setPenaltyValue(policy.getPenaltyValue());
        existing.setDescription(policy.getDescription());
        return policyRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        policyRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void createDefaultsForProvider(Integer providerId) {
        Provider provider = providerRepository.findById(providerId).orElseThrow(() -> new AppException(ErrorCode.PROVIDER_NOT_FOUND));

        // First: before 7 days (>168 hours) -> 0% (keep max null)
        ProviderCancellationPolicy p1 = ProviderCancellationPolicy.builder()
                .provider(provider)
                .minHoursBefore(169)
                .maxHoursBefore(null)
                .penaltyType("PERCENT")
                .penaltyValue(new BigDecimal("0"))
                .description("Hủy trước hơn 7 ngày: 0%")
                .build();
        policyRepository.save(p1);

        // Second: between 7 and 3 days (72 - 167) -> 50%
        ProviderCancellationPolicy p2 = ProviderCancellationPolicy.builder()
                .provider(provider)
                .minHoursBefore(72)
                .maxHoursBefore(167)
                .penaltyType("PERCENT")
                .penaltyValue(new BigDecimal("50"))
                .description("Hủy trong 7-3 ngày trước: 50%")
                .build();
        policyRepository.save(p2);

        // Third: within 3 days (0 - 71) -> 100%
        ProviderCancellationPolicy p3 = ProviderCancellationPolicy.builder()
                .provider(provider)
                .minHoursBefore(0)
                .maxHoursBefore(71)
                .penaltyType("PERCENT")
                .penaltyValue(new BigDecimal("100"))
                .description("Hủy trong 3 ngày trước: 100%")
                .build();
        policyRepository.save(p3);
    }

    @Override
    @Transactional
    public int createDefaultsForProvidersMissingPolicies() {
        List<Provider> providers = providerRepository.findAll();
        int seeded = 0;
        for (Provider prov : providers) {
            Integer pid = prov.getId();
            if (pid == null) continue;
            List<ProviderCancellationPolicy> existing = policyRepository.findByProviderId(pid);
            if (existing == null || existing.isEmpty()) {
                try {
                    createDefaultsForProvider(pid);
                    seeded++;
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(CancellationPolicyServiceImpl.class).error("Failed to seed policies for provider {}: {}", pid, e.getMessage(), e);
                }
            }
        }
        return seeded;
    }
}

