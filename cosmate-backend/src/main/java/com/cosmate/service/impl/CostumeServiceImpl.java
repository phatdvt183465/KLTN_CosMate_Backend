package com.cosmate.service.impl;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.CostumeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeServiceImpl implements CostumeService {

    private final CostumeRepository costumeRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;
    // private final FirebaseService firebaseService;

    @Override
    public List<CostumeResponse> getByProviderId(Integer providerId) {
        return costumeRepository.findByProviderIdAndStatusNotIgnoreCase(providerId, "DELETED").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CostumeResponse createCostume(CostumeRequest request) {
        validateCreateRequest(request);

        Costume costume = new Costume();

        // Mapping toàn bộ dữ liệu cho trường hợp tạo mới
        costume.setProviderId(request.getProviderId());
        costume.setName(request.getName());
        costume.setDescription(request.getDescription());
        costume.setSize(request.getSize());
        costume.setRentPurpose(request.getRentPurpose());
        costume.setNumberOfItems(request.getNumberOfItems());
        costume.setPricePerDay(request.getPricePerDay());
        costume.setDepositAmount(request.getDepositAmount());
        costume.setStatus("AVAILABLE");

        handleImages(costume, request.getImageFiles());
        handleSurcharges(costume, request.getSurcharges());

        return mapToResponse(costumeRepository.save(costume));
    }

    @Override
    @Transactional
    public CostumeResponse updateCostume(Integer id, CostumeRequest request) {
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + id + " not found."));

        // 1. Validate Provider (nếu có thay đổi)
        if (request.getProviderId() != null) {
            if (!providerRepository.existsById(request.getProviderId())) {
                throw new RuntimeException("Error: Provider ID " + request.getProviderId() + " does not exist.");
            }
            costume.setProviderId(request.getProviderId());
        }

        // 2. Partial Update (Logic: Chỉ update khi giá trị != null VÀ không phải chuỗi rỗng)
        // Fix lỗi: Form-data gửi chuỗi rỗng "" khiến dữ liệu cũ bị mất.
        if (isValidString(request.getName())) costume.setName(request.getName());
        if (isValidString(request.getDescription())) costume.setDescription(request.getDescription());
        if (isValidString(request.getSize())) costume.setSize(request.getSize());
        if (isValidString(request.getRentPurpose())) costume.setRentPurpose(request.getRentPurpose());

        // Với số thì chỉ cần check != null
        if (request.getNumberOfItems() != null) costume.setNumberOfItems(request.getNumberOfItems());

        if (request.getPricePerDay() != null) {
            if (request.getPricePerDay().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("Price must be greater than 0");
            costume.setPricePerDay(request.getPricePerDay());
        }

        if (request.getDepositAmount() != null) {
            if (request.getDepositAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new RuntimeException("Deposit cannot be negative");
            costume.setDepositAmount(request.getDepositAmount());
        }

        // 3. Xử lý Ảnh (Image Handling)
        // Logic: Chỉ xóa ảnh cũ và thêm mới nếu request có chứa ít nhất 1 file hợp lệ (size > 0).
        // Nếu request.getImageFiles() là null hoặc list rỗng hoặc toàn file rỗng -> Giữ nguyên ảnh cũ.
        boolean hasNewValidImage = request.getImageFiles() != null &&
                request.getImageFiles().stream().anyMatch(f -> f != null && !f.isEmpty());

        if (hasNewValidImage) {
            costume.getImages().clear(); // Xóa ảnh cũ (orphanRemoval = true sẽ xóa trong DB)
            handleImages(costume, request.getImageFiles());
        }

        // 4. Xử lý Phụ phí (Surcharges)
        // Tương tự: Chỉ update nếu chuỗi JSON có dữ liệu
        if (isValidString(request.getSurcharges())) {
            costume.getSurcharges().clear();
            handleSurcharges(costume, request.getSurcharges());
        }

        return mapToResponse(costumeRepository.save(costume));
    }

    // Helper check chuỗi: Không null và không rỗng
    private boolean isValidString(String input) {
        return input != null && !input.trim().isEmpty();
    }

    @Override
    @Transactional
    public void deleteCostume(Integer id) {
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume not found for deletion."));
        costume.setStatus("DELETED");
        costumeRepository.save(costume);
    }

    @Override
    public List<CostumeResponse> getAllCostumes() {
        return costumeRepository.findAll().stream()
                .filter(c -> !"DELETED".equalsIgnoreCase(c.getStatus()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CostumeResponse getById(Integer id) {
        return mapToResponse(costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume not found.")));
    }

    @Override
    @Transactional
    public void changeStatus(Integer id, String newStatus) {
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + id + " not found."));

        List<String> validStatuses = Arrays.asList("AVAILABLE", "DISABLED", "MAINTENANCE");
        if (!validStatuses.contains(newStatus)) {
            throw new RuntimeException("Invalid status. Allowed: AVAILABLE, DISABLED, MAINTENANCE");
        }
        if ("DELETED".equals(costume.getStatus())) {
            throw new RuntimeException("Cannot change status of a deleted costume.");
        }

        costume.setStatus(newStatus);
        costumeRepository.save(costume);
    }

    // --- PRIVATE HELPERS ---

    private void validateCreateRequest(CostumeRequest request) {
        if (request.getProviderId() == null) throw new RuntimeException("Provider ID is required.");
        if (!providerRepository.existsById(request.getProviderId())) throw new RuntimeException("Provider does not exist.");

        if (!isValidString(request.getName())) throw new RuntimeException("Costume name is required.");
        if (request.getPricePerDay() == null || request.getPricePerDay().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Price must be > 0.");

        // Validate Image on Create
        boolean hasValidImage = request.getImageFiles() != null &&
                request.getImageFiles().stream().anyMatch(f -> f != null && !f.isEmpty());
        if (!hasValidImage) throw new RuntimeException("At least one image is required.");
    }

    private void handleImages(Costume costume, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

        int validImageCount = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            // Mock Firebase Upload
            // String url = firebaseService.upload(file);
            String url = "https://firebase-storage/mock/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

            CostumeImage img = new CostumeImage();
            img.setImageUrl(url);

            // Ảnh đầu tiên hợp lệ sẽ là MAIN
            img.setType(validImageCount == 0 ? "MAIN" : "DETAIL");
            img.setCostume(costume);

            costume.getImages().add(img);
            validImageCount++;
        }
    }

    private void handleSurcharges(Costume costume, String json) {
        if (!isValidString(json)) return;
        try {
            Map<String, Map<String, Object>> surchargeData = objectMapper.readValue(json,
                    new TypeReference<Map<String, Map<String, Object>>>() {});

            surchargeData.forEach((name, details) -> {
                CostumeSurcharge s = new CostumeSurcharge();
                s.setName(name);

                Object price = details.get("price");
                s.setPrice(price != null ? new BigDecimal(price.toString()) : BigDecimal.ZERO);

                Object desc = details.get("description");
                s.setDescription(desc != null ? desc.toString() : "");

                s.setCostume(costume);
                costume.getSurcharges().add(s);
            });
        } catch (IOException e) {
            throw new RuntimeException("Invalid Surcharge JSON format.");
        }
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
                .surcharges(costume.getSurcharges().stream()
                        .map(s -> CostumeResponse.SurchargeResponse.builder()
                                .name(s.getName())
                                .description(s.getDescription())
                                .price(s.getPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}