package com.cosmate.service.impl;

import com.cosmate.dto.request.RentalOptionRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeRentalOption;
import com.cosmate.repository.CostumeRentalOptionRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.service.CostumeRentalOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeRentalOptionServiceImpl implements CostumeRentalOptionService {

    private final CostumeRentalOptionRepository rentalOptionRepository;
    private final CostumeRepository costumeRepository;

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

        if (request.getName() != null) option.setName(request.getName());
        if (request.getPrice() != null) option.setPrice(request.getPrice());
        if (request.getDescription() != null) option.setDescription(request.getDescription());

        return mapToResponse(rentalOptionRepository.save(option));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        if (!rentalOptionRepository.existsById(id)) {
            throw new RuntimeException("Error: Rental Option not found to delete.");
        }
        rentalOptionRepository.deleteById(id);
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