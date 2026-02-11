package com.cosmate.service.impl;

import com.cosmate.dto.request.AccessoryRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeAccessory;
import com.cosmate.repository.CostumeAccessoryRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.service.CostumeAccessoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeAccessoryServiceImpl implements CostumeAccessoryService {

    private final CostumeAccessoryRepository accessoryRepository;
    private final CostumeRepository costumeRepository;

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
        if (request.getName() != null) acc.setName(request.getName());
        if (request.getPrice() != null) acc.setPrice(request.getPrice());
        if (request.getDescription() != null) acc.setDescription(request.getDescription());
        if (request.getIsRequired() != null) acc.setIsRequired(request.getIsRequired());
        return mapToResponse(accessoryRepository.save(acc));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        accessoryRepository.deleteById(id);
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