package com.cosmate.service.impl;

import com.cosmate.dto.request.SurchargeRequest;
import com.cosmate.dto.response.SurchargeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeSurcharge;
import com.cosmate.entity.Provider;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.CostumeSurchargeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.CostumeSurchargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeSurchargeServiceImpl implements CostumeSurchargeService {

    private final CostumeSurchargeRepository surchargeRepository;
    private final CostumeRepository costumeRepository;
    private final ProviderRepository providerRepository;

    @Override
    public List<SurchargeResponse> getByCostumeId(Integer costumeId) {
        if (!costumeRepository.existsById(costumeId)) {
            throw new RuntimeException("Error: Costume ID " + costumeId + " not found.");
        }
        return surchargeRepository.findByCostumeId(costumeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SurchargeResponse create(Integer costumeId, SurchargeRequest request) {
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + costumeId + " not found."));
        ensureCurrentUserOwnsCostume(costume);

        validateRequest(request);

        CostumeSurcharge surcharge = new CostumeSurcharge();
        surcharge.setCostume(costume);
        surcharge.setName(request.getName());
        surcharge.setDescription(request.getDescription());
        surcharge.setPrice(request.getPrice());

        return mapToResponse(surchargeRepository.save(surcharge));
    }

    @Override
    @Transactional
    public SurchargeResponse update(Integer id, SurchargeRequest request) {
        CostumeSurcharge surcharge = surchargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Surcharge ID " + id + " not found."));
        ensureCurrentUserOwnsCostume(surcharge.getCostume());

        // Logic Partial Update (Chỉ update trường khác null/rỗng)
        if (isValidString(request.getName())) {
            surcharge.setName(request.getName());
        }

        if (isValidString(request.getDescription())) {
            surcharge.setDescription(request.getDescription());
        }

        if (request.getPrice() != null) {
            if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Error: Price cannot be negative.");
            }
            surcharge.setPrice(request.getPrice());
        }

        return mapToResponse(surchargeRepository.save(surcharge));
    }

    @Override
    public SurchargeResponse getById(Integer id) {
        return mapToResponse(surchargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Surcharge ID " + id + " not found.")));
    }

    @Override
    @Transactional
    public void deleteSurcharge(Integer id) {
        CostumeSurcharge surcharge = surchargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Surcharge not found to delete."));
        ensureCurrentUserOwnsCostume(surcharge.getCostume());
        surchargeRepository.deleteById(id);
    }

    // --- Helpers ---

    private void validateRequest(SurchargeRequest request) {
        if (!isValidString(request.getName())) {
            throw new RuntimeException("Error: Surcharge name is required.");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Error: Price is required and cannot be negative.");
        }
    }

    private boolean isValidString(String input) {
        return input != null && !input.trim().isEmpty();
    }

    private Integer getCurrentUserIdFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            }
            if (principal instanceof Integer) return (Integer) principal;
            if (principal instanceof Long) return ((Long) principal).intValue();
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority())
                        || "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    private void ensureCurrentUserOwnsCostume(Costume costume) {
        if (isPrivileged()) return;
        Integer currentUserId = getCurrentUserIdFromContext();
        if (currentUserId == null) throw new RuntimeException("Unauthorized");
        Provider provider = providerRepository.findById(costume.getProviderId())
                .orElseThrow(() -> new RuntimeException("Provider not found"));
        if (!currentUserId.equals(provider.getUserId())) {
            throw new RuntimeException("Forbidden: You do not own this costume");
        }
    }

    private SurchargeResponse mapToResponse(CostumeSurcharge entity) {
        return SurchargeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .costumeId(entity.getCostume().getId())
                .build();
    }
}