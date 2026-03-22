package com.cosmate.service.impl;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.AIService;
import com.cosmate.service.CostumeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final AIService aiService;
    private final com.cosmate.service.FirebaseStorageService firebaseStorageService;

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
        mapBaseInfo(costume, request);
        costume.setProviderId(request.getProviderId());
        costume.setStatus("AVAILABLE");

        // Xử lý các thành phần liên quan
        handleImages(costume, request.getImageFiles());
        handleSurcharges(costume, request.getSurcharges());
        handleAccessories(costume, request.getAccessories());
        handleRentalOptions(costume, request.getRentalOptions());

        return mapToResponse(costumeRepository.save(costume));
    }

    @Override
    @Transactional
    public CostumeResponse updateCostume(Integer id, CostumeRequest request) {
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + id + " not found."));

        // 1. Cập nhật Provider nếu hợp lệ
        if (request.getProviderId() != null) {
            if (!providerRepository.existsById(request.getProviderId())) {
                throw new RuntimeException("Error: Provider ID " + request.getProviderId() + " does not exist.");
            }
            costume.setProviderId(request.getProviderId());
        }

        // 2. Cập nhật thông tin cơ bản (Partial Update)
        updateBaseInfo(costume, request);

        // 3. Xử lý Ảnh (Chỉ thay thế nếu có ít nhất 1 file mới hợp lệ)
        boolean hasNewImage = request.getImageFiles() != null &&
                request.getImageFiles().stream().anyMatch(f -> f != null && !f.isEmpty());
        if (hasNewImage) {
            costume.getImages().clear();
            handleImages(costume, request.getImageFiles());
        }

        // 4. Xử lý Phụ phí
        if (isValidString(request.getSurcharges())) {
            costume.getSurcharges().clear();
            handleSurcharges(costume, request.getSurcharges());
        }

        // 5. Xử lý Phụ kiện (Task 4)
        if (isValidString(request.getAccessories())) {
            costume.getAccessories().clear();
            handleAccessories(costume, request.getAccessories());
        }

        // 6. Xử lý Gói thuê (Task 4)
        if (isValidString(request.getRentalOptions())) {
            costume.getRentalOptions().clear();
            handleRentalOptions(costume, request.getRentalOptions());
        }

        return mapToResponse(costumeRepository.save(costume));
    }

    @Override
    @Transactional
    public void deleteCostume(Integer id) {
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume not found."));
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
            throw new RuntimeException("Invalid status. Allowed: " + validStatuses);
        }
        if ("DELETED".equals(costume.getStatus())) {
            throw new RuntimeException("Cannot change status of a deleted costume.");
        }

        costume.setStatus(newStatus);
        costumeRepository.save(costume);
    }

    // --- Private Business Logic Helpers ---

    private void mapBaseInfo(Costume costume, CostumeRequest request) {
        costume.setName(request.getName());
        costume.setDescription(request.getDescription());
        costume.setSize(request.getSize());
        costume.setRentPurpose(request.getRentPurpose());
        costume.setNumberOfItems(request.getNumberOfItems());
        costume.setPricePerDay(request.getPricePerDay());
        costume.setDepositAmount(request.getDepositAmount());
    }

    private void updateBaseInfo(Costume costume, CostumeRequest request) {
        if (isValidString(request.getName())) costume.setName(request.getName());
        if (isValidString(request.getDescription())) costume.setDescription(request.getDescription());
        if (isValidString(request.getSize())) costume.setSize(request.getSize());
        if (isValidString(request.getRentPurpose())) costume.setRentPurpose(request.getRentPurpose());
        if (request.getNumberOfItems() != null) costume.setNumberOfItems(request.getNumberOfItems());

        if (request.getPricePerDay() != null) {
            if (request.getPricePerDay().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("Price must be > 0");
            costume.setPricePerDay(request.getPricePerDay());
        }
        if (request.getDepositAmount() != null) {
            if (request.getDepositAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new RuntimeException("Deposit cannot be negative");
            costume.setDepositAmount(request.getDepositAmount());
        }
    }

    /**
     * Xử lý danh sách file ảnh của trang phục.
     * Áp dụng:
     * 1. Gom nhóm kiểm duyệt AI (1 Request/List).
     * 2. Tái sử dụng Vector (1 Request/Bộ đồ).
     * 3. Xử lý bất đồng bộ đa luồng (Multi-threading) khi upload Firebase.
     */
    private void handleImages(Costume costume, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

        // [TỐI ƯU 1] Kiểm duyệt 18+ toàn bộ ảnh trong 1 lần gọi AI
        aiService.validateMultipleImageContents(files);

        // [TỐI ƯU 2] Tạo vector nhúng 1 lần duy nhất bằng Text và dùng chung cho mọi ảnh
        String textForVector = costume.getName() + " " + costume.getDescription();
        String costumeVector = aiService.generateVectorForText(textForVector);

        // [TỐI ƯU 3] Upload ảnh lên Firebase đồng thời bằng ThreadPool
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(files.size(), 10));
        List<CompletableFuture<CostumeImage>> futures = new ArrayList<>();
        int index = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            final boolean isMainImage = (index == 0); // Ảnh đầu tiên mặc định là MAIN
            index++;

            CompletableFuture<CostumeImage> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String original = file.getOriginalFilename();
                    String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String folderName = costume.getId() != null ? String.valueOf(costume.getId()) : "new_" + System.currentTimeMillis();
                    String path = String.format("costumes/%s/%d_%s", folderName, System.currentTimeMillis(), safeName);

                    // Upload bất đồng bộ
                    String imageUrl = firebaseStorageService.uploadFile(file, path);

                    CostumeImage img = new CostumeImage();
                    img.setImageUrl(imageUrl);
                    img.setType(isMainImage ? "MAIN" : "DETAIL");
                    img.setCostume(costume);
                    img.setImageVector(costumeVector); // Dán vector dùng chung

                    return img;
                } catch (Exception e) {
                    throw new RuntimeException("Lỗi upload ảnh lên Cloud: " + e.getMessage(), e);
                }
            }, executor);

            futures.add(future);
        }

        // Đợi tất cả luồng hoàn thành và lưu vào danh sách của entity
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<CostumeImage> future : futures) {
                costume.getImages().add(future.get());
            }
        } catch (Exception e) {
            throw new RuntimeException("Tiến trình upload ảnh thất bại: " + e.getCause().getMessage(), e);
        } finally {
            executor.shutdown(); // Giải phóng tài nguyên ThreadPool
        }
    }

    private void handleSurcharges(Costume costume, String json) {
        if (!isValidString(json)) return;
        try {
            List<Map<String, Object>> data = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> item : data) {
                CostumeSurcharge s = new CostumeSurcharge();
                s.setName(item.get("name").toString()); // Lấy "name" từ object thay vì lấy từ key của Map
                s.setPrice(new BigDecimal(item.get("price").toString()));
                s.setDescription(item.getOrDefault("description", "").toString());
                s.setCostume(costume);
                costume.getSurcharges().add(s);
            }
        } catch (IOException e) {
            throw new RuntimeException("Invalid Surcharge JSON format.");
        }
    }

    private void handleAccessories(Costume costume, String json) {
        if (!isValidString(json)) return;
        try {
            List<Map<String, Object>> data = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> item : data) {
                CostumeAccessory acc = new CostumeAccessory();
                acc.setName(item.get("name").toString());
                acc.setPrice(new BigDecimal(item.get("price").toString()));
                acc.setDescription(item.getOrDefault("description", "").toString());
                acc.setIsRequired(Boolean.parseBoolean(item.getOrDefault("isRequired", "false").toString()));
                acc.setStatus("ACTIVE");
                acc.setCostume(costume);
                costume.getAccessories().add(acc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Invalid Accessories JSON format.");
        }
    }

    private void handleRentalOptions(Costume costume, String json) {
        if (!isValidString(json)) return;
        try {
            List<Map<String, Object>> data = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> item : data) {
                CostumeRentalOption opt = new CostumeRentalOption();
                opt.setName(item.get("name").toString());
                opt.setPrice(new BigDecimal(item.get("price").toString()));
                opt.setDescription(item.getOrDefault("description", "").toString());
                opt.setStatus("ACTIVE");
                opt.setCostume(costume);
                costume.getRentalOptions().add(opt);
            }
        } catch (IOException e) {
            throw new RuntimeException("Invalid Rental Options JSON format.");
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
                                .id(s.getId()).name(s.getName()).description(s.getDescription()).price(s.getPrice()).build())
                        .collect(Collectors.toList()))
                .accessories(costume.getAccessories().stream()
                        .map(a -> CostumeResponse.AccessoryResponse.builder()
                                .id(a.getId()).name(a.getName()).description(a.getDescription()).price(a.getPrice()).isRequired(a.getIsRequired()).build())
                        .collect(Collectors.toList()))
                .rentalOptions(costume.getRentalOptions().stream()
                        .map(o -> CostumeResponse.RentalOptionResponse.builder()
                                .id(o.getId()).name(o.getName()).description(o.getDescription()).price(o.getPrice()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    private void validateCreateRequest(CostumeRequest request) {
        if (request.getProviderId() == null || !providerRepository.existsById(request.getProviderId())) {
            throw new RuntimeException("Valid Provider ID is required.");
        }
        if (!isValidString(request.getName())) throw new RuntimeException("Costume name is required.");
        if (request.getPricePerDay() == null || request.getPricePerDay().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Price must be greater than 0.");
        }
        boolean hasImage = request.getImageFiles() != null &&
                request.getImageFiles().stream().anyMatch(f -> f != null && !f.isEmpty());
        if (!hasImage) throw new RuntimeException("At least one valid image is required.");
    }

    private boolean isValidString(String input) {
        return input != null && !input.trim().isEmpty();
    }

    // --- API SEARCH CHỦ ĐỘNG ---
    @Override
    public List<CostumeResponse> searchCostumes(String keyword) {
        List<Costume> costumes = costumeRepository.findByNameContainingIgnoreCaseAndStatusNot(keyword, "DELETED");

        return costumes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}