package com.cosmate.service.impl;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.service.CostumeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeServiceImpl implements CostumeService {

    private final CostumeRepository costumeRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CostumeResponse createCostume(CostumeRequest request) {
        Costume costume = new Costume();
        // Khi tạo mới thì gán hết
        mapRequestToEntity(costume, request);
        costume.setStatus("AVAILABLE");

        handleImages(costume, request.getImageFiles());
        handleSurcharges(costume, request.getSurcharges());

        return mapToResponse(costumeRepository.save(costume));
    }

    @Override
    @Transactional
    public CostumeResponse updateCostume(Long id, CostumeRequest request) {
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bộ đồ ID: " + id));

        // --- PHẦN QUAN TRỌNG: CHỈ CẬP NHẬT NẾU KHÁC NULL ---
        if (request.getName() != null && !request.getName().isBlank())
            costume.setName(request.getName());

        if (request.getDescription() != null)
            costume.setDescription(request.getDescription());

        if (request.getSize() != null)
            costume.setSize(request.getSize());

        if (request.getRentPurpose() != null)
            costume.setRentPurpose(request.getRentPurpose());

        if (request.getNumberOfItems() != null)
            costume.setNumberOfItems(request.getNumberOfItems());

        if (request.getPricePerDay() != null)
            costume.setPricePerDay(request.getPricePerDay());

        if (request.getDepositAmount() != null)
            costume.setDepositAmount(request.getDepositAmount());

        if (request.getProviderId() != null)
            costume.setProviderId(request.getProviderId());

        // CHỈ cập nhật ảnh nếu có up file mới lên
        if (request.getImageFiles() != null && !request.getImageFiles().isEmpty()) {
            costume.getImages().clear(); // Xóa ảnh cũ
            handleImages(costume, request.getImageFiles());
        }

        // CHỈ cập nhật phụ phí nếu có gửi chuỗi JSON mới và không rỗng
        if (request.getSurcharges() != null && !request.getSurcharges().isBlank()) {
            costume.getSurcharges().clear(); // Xóa phụ phí cũ
            handleSurcharges(costume, request.getSurcharges());
        }

        return mapToResponse(costumeRepository.save(costume));
    }

    private void handleImages(Costume costume, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        for (int i = 0; i < files.size(); i++) {
            String path = saveFileLocal(files.get(i));
            CostumeImage img = new CostumeImage();
            img.setImageUrl(path);
            img.setType(i == 0 ? "MAIN" : "DETAIL");
            img.setCostume(costume);
            costume.getImages().add(img);
        }
    }

    private void handleSurcharges(Costume costume, String json) {
        if (json == null || json.isBlank()) return;
        try {
            // Cấu trúc JSON có description: {"Tên phí": {"price": 100, "description": "mô tả"}}
            Map<String, Map<String, Object>> surchargeData = objectMapper.readValue(json,
                    new TypeReference<Map<String, Map<String, Object>>>() {});

            surchargeData.forEach((name, details) -> {
                CostumeSurcharge s = new CostumeSurcharge();
                s.setName(name);

                Object price = details.get("price");
                s.setPrice(price != null ? new BigDecimal(price.toString()) : BigDecimal.ZERO);

                Object desc = details.get("description");
                s.setDescription(desc != null ? desc.toString() : ""); // Lưu description vào DB

                s.setCostume(costume);
                costume.getSurcharges().add(s);
            });
        } catch (IOException e) {
            throw new RuntimeException("JSON phụ phí sai định dạng!");
        }
    }

    private String saveFileLocal(MultipartFile file) {
        try {
            String dir = "uploads/costumes/";
            Files.createDirectories(Paths.get(dir));
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename().replaceAll("\\s+", "_");
            Path path = Paths.get(dir + fileName);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            return "/" + dir + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi lưu file vật lý!");
        }
    }

    private void mapRequestToEntity(Costume costume, CostumeRequest request) {
        costume.setName(request.getName());
        costume.setDescription(request.getDescription());
        costume.setSize(request.getSize());
        costume.setRentPurpose(request.getRentPurpose());
        costume.setNumberOfItems(request.getNumberOfItems());
        costume.setPricePerDay(request.getPricePerDay());
        costume.setDepositAmount(request.getDepositAmount());
        costume.setProviderId(request.getProviderId());
    }

    private CostumeResponse mapToResponse(Costume costume) {
        return CostumeResponse.builder()
                .id(costume.getId())
                .name(costume.getName())
                .description(costume.getDescription())
                .size(costume.getSize())
                .rentPurpose(costume.getRentPurpose())
                .numberOfItems(costume.getNumberOfItems())
                .pricePerDay(costume.getPricePerDay())
                .depositAmount(costume.getDepositAmount())
                .status(costume.getStatus())
                .providerId(costume.getProviderId())
                .imageUrls(costume.getImages().stream().map(CostumeImage::getImageUrl).collect(Collectors.toList()))
                .build();
    }

    @Override public List<CostumeResponse> getAllCostumes() { return costumeRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList()); }
    @Override public CostumeResponse getById(Long id) { return mapToResponse(costumeRepository.findById(id).orElseThrow()); }
    @Override public void deleteCostume(Long id) { costumeRepository.deleteById(id); }
}