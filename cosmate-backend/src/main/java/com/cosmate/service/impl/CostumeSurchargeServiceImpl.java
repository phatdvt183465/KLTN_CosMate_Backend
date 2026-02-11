package com.cosmate.service.impl;

import com.cosmate.dto.request.SurchargeRequest;
import com.cosmate.dto.response.SurchargeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeSurcharge;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.CostumeSurchargeRepository;
import com.cosmate.service.CostumeSurchargeService;
import lombok.RequiredArgsConstructor;
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
        if (!surchargeRepository.existsById(id)) {
            throw new RuntimeException("Error: Surcharge not found to delete.");
        }
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