package com.cosmate.service.impl;

import com.cosmate.dto.request.AccessoryRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeAccessory;
import com.cosmate.entity.Provider;
import com.cosmate.repository.CostumeAccessoryRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.CostumeAccessoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeAccessoryServiceImpl implements CostumeAccessoryService {

    private final CostumeAccessoryRepository accessoryRepository;
    private final CostumeRepository costumeRepository;
    private final ProviderRepository providerRepository;

    @Override
    public List<CostumeResponse.AccessoryResponse> getByCostumeId(Integer costumeId) {
        return accessoryRepository.findByCostumeId(costumeId).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public CostumeResponse.AccessoryResponse getById(Integer id) {
        return mapToResponse(accessoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Accessory not found")));
    }

    @Override
    @Transactional
    public CostumeResponse.AccessoryResponse create(Integer costumeId, AccessoryRequest request) {
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Costume not found"));
        ensureCurrentUserOwnsCostume(costume);
        CostumeAccessory acc = new CostumeAccessory();
        acc.setCostume(costume);
        acc.setName(request.getName());
        acc.setPrice(request.getPrice());
        acc.setDescription(request.getDescription());
        acc.setIsRequired(request.getIsRequired() != null ? request.getIsRequired() : false);
        acc.setStatus("ACTIVE");
        return mapToResponse(accessoryRepository.save(acc));
    }

    @Override
    @Transactional
    public CostumeResponse.AccessoryResponse update(Integer id, AccessoryRequest request) {
        CostumeAccessory acc = accessoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Accessory not found"));
        ensureCurrentUserOwnsCostume(acc.getCostume());
        if (request.getName() != null) acc.setName(request.getName());
        if (request.getPrice() != null) acc.setPrice(request.getPrice());
        if (request.getDescription() != null) acc.setDescription(request.getDescription());
        if (request.getIsRequired() != null) acc.setIsRequired(request.getIsRequired());
        return mapToResponse(accessoryRepository.save(acc));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        CostumeAccessory acc = accessoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Accessory not found"));
        ensureCurrentUserOwnsCostume(acc.getCostume());
        accessoryRepository.deleteById(id);
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

    private CostumeResponse.AccessoryResponse mapToResponse(CostumeAccessory entity) {
        return CostumeResponse.AccessoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .isRequired(entity.getIsRequired())
                .build();
    }
}