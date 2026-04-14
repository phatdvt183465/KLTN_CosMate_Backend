package com.cosmate.service.impl;

import com.cosmate.dto.request.RentalOptionRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeRentalOption;
import com.cosmate.entity.Provider;
import com.cosmate.repository.CostumeRentalOptionRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.CostumeRentalOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeRentalOptionServiceImpl implements CostumeRentalOptionService {

    private final CostumeRentalOptionRepository rentalOptionRepository;
    private final CostumeRepository costumeRepository;
    private final ProviderRepository providerRepository;

    @Override
    public List<CostumeResponse.RentalOptionResponse> getByCostumeId(Integer costumeId) {
        return rentalOptionRepository.findByCostumeId(costumeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CostumeResponse.RentalOptionResponse getById(Integer id) {
        return mapToResponse(rentalOptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Rental Option ID " + id + " not found.")));
    }

    @Override
    @Transactional
    public CostumeResponse.RentalOptionResponse create(Integer costumeId, RentalOptionRequest request) {
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + costumeId + " not found."));
        ensureCurrentUserOwnsCostume(costume);

        CostumeRentalOption option = new CostumeRentalOption();
        option.setCostume(costume);
        option.setName(request.getName());
        option.setPrice(request.getPrice());
        option.setDescription(request.getDescription());
        option.setStatus("ACTIVE");

        return mapToResponse(rentalOptionRepository.save(option));
    }

    @Override
    @Transactional
    public CostumeResponse.RentalOptionResponse update(Integer id, RentalOptionRequest request) {
        CostumeRentalOption option = rentalOptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Rental Option ID " + id + " not found."));
        ensureCurrentUserOwnsCostume(option.getCostume());

        if (request.getName() != null) option.setName(request.getName());
        if (request.getPrice() != null) option.setPrice(request.getPrice());
        if (request.getDescription() != null) option.setDescription(request.getDescription());

        return mapToResponse(rentalOptionRepository.save(option));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        CostumeRentalOption option = rentalOptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Rental Option not found to delete."));
        ensureCurrentUserOwnsCostume(option.getCostume());
        rentalOptionRepository.deleteById(id);
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

    private CostumeResponse.RentalOptionResponse mapToResponse(CostumeRentalOption entity) {
        return CostumeResponse.RentalOptionResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .price(entity.getPrice())
                .description(entity.getDescription())
                .build();
    }
}