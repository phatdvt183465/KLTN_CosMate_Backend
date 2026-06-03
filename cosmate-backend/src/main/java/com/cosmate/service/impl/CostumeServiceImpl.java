package com.cosmate.service.impl;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeAccessory;
import com.cosmate.entity.CostumeImage;
import com.cosmate.entity.CostumeRentalOption;
import com.cosmate.entity.CostumeSurcharge;
import com.cosmate.entity.Notification;
import com.cosmate.entity.Provider;
import com.cosmate.entity.WishlistCostume;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.AIService;
import com.cosmate.service.CostumeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostumeServiceImpl implements CostumeService {

    private final CostumeRepository costumeRepository;
    private final ProviderRepository providerRepository;
    private final com.cosmate.repository.WishlistRepository wishlistRepository;
    private final com.cosmate.service.NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AIService aiService;
    private final FirebaseStorageServiceImpl firebaseStorageService;
    private final com.cosmate.repository.CharacterRepository characterRepository;

    @Override
    public List<CostumeResponse> getByProviderId(Integer providerId) {
        return costumeRepository.findByProviderIdAndStatusNotIgnoreCase(providerId, "DELETED").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CostumeResponse createCostume(Integer currentUserId, CostumeRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        validateCreateRequest(currentUserId, request);

        // Bước 1: Validate 18+ (Đồng bộ - Sync)
        List<MultipartFile> imageFilesForAi = new ArrayList<>();
        if (request.getImageFiles() != null) {
            imageFilesForAi.addAll(request.getImageFiles().stream()
                    .filter(f -> f != null && !f.isEmpty() && f.getContentType() != null && f.getContentType().startsWith("image/"))
                    .collect(Collectors.toList()));
        }
        if (request.getVideoFiles() != null) {
            imageFilesForAi.addAll(request.getVideoFiles().stream()
                    .filter(f -> f != null && !f.isEmpty() && f.getContentType() != null && f.getContentType().startsWith("image/"))
                    .collect(Collectors.toList()));
        }

        if (!imageFilesForAi.isEmpty()) {
            String moderation = aiService.moderateCostumeImages(imageFilesForAi);
            if ("UNSAFE_VIOLATION".equals(moderation) || "UNSAFE_IRRELEVANT".equals(moderation)) {
                throw new AppException(ErrorCode.IMAGE_POLICY_VIOLATION);
            }
        }

        // Bước 2: Khởi tạo và Lưu DB (Đồng bộ - Sync)
        Costume costume = new Costume();
        mapBaseInfo(costume, request);
        // Ensure a default rentDiscount is set to avoid null DB inserts and to have consistent pricing behavior
        if (costume.getRentDiscount() == null) {
            costume.setRentDiscount(100); // default = 100% (no discount for subsequent days)
        }
        costume.setProviderId(request.getProviderId());
        costume.setStatus("PENDING"); // Khởi tạo với trạng thái PENDING

        // Xử lý các thành phần liên quan
        handleImages(costume, request);
        handleSurcharges(costume, request.getSurcharges());
        handleAccessories(costume, request.getAccessories());
        handleRentalOptions(costume, request.getRentalOptions());

        // Lưu bộ đồ để lấy ID và URL ảnh chính thức
        Costume savedCostume = costumeRepository.save(costume);

        // Chọn Characters
        if (request.getCharacterIds() != null && !request.getCharacterIds().isEmpty()) {
            List<com.cosmate.entity.Character> chars = characterRepository.findAllById(request.getCharacterIds());
            savedCostume.setCharacters(chars);
            savedCostume = costumeRepository.save(savedCostume);
        }

        // Bước 3: Tạo Vector Embeddings (Bất đồng bộ - Async)
        aiService.processNewCostumeAsync(savedCostume.getId());

        return mapToResponse(savedCostume);
    }

    @Override
    @Transactional
    public CostumeResponse updateCostume(Integer currentUserId, Integer id, CostumeRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.COSTUME_NOT_FOUND));

        ensureCurrentUserOwnsCostume(currentUserId, costume);

        // --- ĐẶT LÍNH GÁC: Kiểm tra xem Tên và Mô tả có bị đổi không? ---
        boolean isTextChanged = false;
        String oldName = costume.getName() != null ? costume.getName() : "";
        String newName = request.getName() != null ? request.getName() : "";
        String oldDesc = costume.getDescription() != null ? costume.getDescription() : "";
        String newDesc = request.getDescription() != null ? request.getDescription() : "";

        if (!oldName.equals(newName) || !oldDesc.equals(newDesc)) {
            isTextChanged = true;
        }

        // Kiểm tra xem có up file ảnh mới nào không?
        boolean isImageChanged = (request.getImageFiles() != null && request.getImageFiles().stream().anyMatch(f -> f != null && !f.isEmpty())) ||
                                 (request.getVideoFiles() != null && request.getVideoFiles().stream().anyMatch(f -> f != null && !f.isEmpty()));

        // 1. Không cho phép đổi quyền sở hữu costume qua API update
        if (request.getProviderId() != null && !request.getProviderId().equals(costume.getProviderId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 2. Cập nhật thông tin cơ bản (Partial Update)
        updateBaseInfo(costume, request);

        // 3. Xử lý Ảnh (Chỉ thay thế nếu có ít nhất 1 file mới hợp lệ)
        if (isImageChanged) {
            validateMediaLimits(request);
            costume.getImages().clear();
            handleImages(costume, request);
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

        // Chọn Characters
        if (request.getCharacterIds() != null && !request.getCharacterIds().isEmpty()) {
            List<com.cosmate.entity.Character> chars = characterRepository.findAllById(request.getCharacterIds());
            costume.setCharacters(chars);
        }

        if (isTextChanged || isImageChanged) {
            registerVectorGenerationAfterCommit(costume.getId(), isTextChanged, isImageChanged);
        }

        return mapToResponse(costume); // Thay cho costumeRepository.save(costume) nếu không cần thiết
    }

    @Override
    @Transactional
    public void deleteCostume(Integer currentUserId, Integer id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.COSTUME_NOT_FOUND));
        ensureCurrentUserOwnsCostume(currentUserId, costume);

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
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume not found."));
        if ("DELETED".equalsIgnoreCase(costume.getStatus())) {
            throw new AppException(ErrorCode.COSTUME_NOT_FOUND);
        }
        return mapToResponse(costume);
    }

    @Override
    @Transactional
    public void changeStatus(Integer currentUserId, Integer id, String newStatus) {
        if (currentUserId == null) {
            throw new RuntimeException("Unauthorized");
        }
        Costume costume = costumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + id + " not found."));

        ensureCurrentUserOwnsCostume(currentUserId, costume);

        List<String> validStatuses = Arrays.asList("AVAILABLE", "DISABLED", "MAINTENANCE");
        if (!validStatuses.contains(newStatus)) {
            throw new AppException(ErrorCode.INVALID_COSTUME_STATUS);
        }
        if ("DELETED".equals(costume.getStatus())) {
            throw new AppException(ErrorCode.COSTUME_DELETED);
        }

        costume.setStatus(newStatus);
        costumeRepository.save(costume);

        // If it becomes AVAILABLE, notify wishlist users
        if ("AVAILABLE".equalsIgnoreCase(newStatus)) {
            try {
                List<com.cosmate.entity.WishlistCostume> watchers = wishlistRepository.findAllByCostumeId(costume.getId());
                if (watchers != null && !watchers.isEmpty()) {
                    for (com.cosmate.entity.WishlistCostume w : watchers) {
                        try {
                            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(w.getUserId()).build())
                                    .type("WISHLIST_NOTIFY")
                                    .header("Bộ đồ bạn quan tâm đã có sẵn")
                                    .content("Bộ đồ '" + costume.getName() + "' hiện đã có sẵn để thuê.")
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(n);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // --- Private Business Logic Helpers ---

    private void registerVectorGenerationAfterCommit(Integer costumeId, boolean updateText, boolean updateImage) {
        if (!updateText && !updateImage) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiService.generateAndSaveVector(costumeId, updateText, updateImage);
                }
            });
        } else {
            aiService.generateAndSaveVector(costumeId, updateText, updateImage);
        }
    }

    private void mapBaseInfo(Costume costume, CostumeRequest request) {
        costume.setName(request.getName());
        costume.setDescription(request.getDescription());
        costume.setSize(request.getSize());
        costume.setRentPurpose(request.getRentPurpose());
        costume.setNumberOfItems(request.getNumberOfItems());
        costume.setPricePerDay(request.getPricePerDay());
        costume.setCost(request.getCost());
        costume.setDepositAmount(request.getDepositAmount());
        costume.setGender(request.getGender());
        if (request.getRentDiscount() != null) costume.setRentDiscount(request.getRentDiscount());
    }

    private void updateBaseInfo(Costume costume, CostumeRequest request) {
        if (isValidString(request.getName())) costume.setName(request.getName());
        if (isValidString(request.getDescription())) costume.setDescription(request.getDescription());
        if (isValidString(request.getSize())) costume.setSize(request.getSize());
        if (isValidString(request.getRentPurpose())) costume.setRentPurpose(request.getRentPurpose());
        if (request.getNumberOfItems() != null) costume.setNumberOfItems(request.getNumberOfItems());

        // Cross-field validation: resulting cost must be >= resulting depositAmount
        java.math.BigDecimal resultingCost = request.getCost() != null ? request.getCost() : costume.getCost();
        java.math.BigDecimal resultingDeposit = request.getDepositAmount() != null ? request.getDepositAmount() : costume.getDepositAmount();
        if (resultingCost != null && resultingDeposit != null) {
            if (resultingCost.compareTo(resultingDeposit) < 0) {
                throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            }
        }

        if (request.getPricePerDay() != null) {
            if (request.getPricePerDay().compareTo(BigDecimal.ZERO) <= 0)
                throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            costume.setPricePerDay(request.getPricePerDay());
        }
        if (request.getDepositAmount() != null) {
            if (request.getDepositAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            costume.setDepositAmount(request.getDepositAmount());
        }
        if (request.getCost() != null) {
            if (request.getCost().compareTo(BigDecimal.ZERO) < 0)
                throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            costume.setCost(request.getCost());
        }
        if (request.getRentDiscount() != null) {
            if (request.getRentDiscount() < 0 || request.getRentDiscount() > 100)
                throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            costume.setRentDiscount(request.getRentDiscount());
        }
        // Gender update (optional)
        if (isValidString(request.getGender())) {
            String g = request.getGender().trim().toUpperCase();
            List<String> allowed = Arrays.asList("MALE", "FEMALE", "UNISEX", "GENDERLESS");
            if (!allowed.contains(g)) throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            costume.setGender(g);
        }
    }

    /**
     * Xử lý danh sách file ảnh của trang phục.
     * Áp dụng:
     * 1. Gom nhóm kiểm duyệt AI (1 Request/List).
     * 2. Tái sử dụng Vector (1 Request/Bộ đồ).
     * 3. Xử lý bất đồng bộ đa luồng (Multi-threading) khi upload Firebase.
     */
    private void handleImages(Costume costume, CostumeRequest request) {
        List<MultipartFile> files = new ArrayList<>();
        if (request.getImageFiles() != null) files.addAll(request.getImageFiles());
        if (request.getVideoFiles() != null) files.addAll(request.getVideoFiles());

        if (files.isEmpty()) return;

        // Giữ lại kiểm duyệt nội dung ảnh (chỉ kiểm duyệt hình ảnh)
        List<MultipartFile> imagesToValidate = files.stream()
                .filter(f -> f != null && !f.isEmpty() && f.getContentType() != null && f.getContentType().startsWith("image/"))
                .collect(Collectors.toList());
        if (!imagesToValidate.isEmpty()) {
            aiService.validateMultipleImageContents(imagesToValidate);
        }

        // Chỉ upload ảnh, không tạo vector lúc create/update để tránh timeout
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(files.size(), 10));
        List<CompletableFuture<CostumeImage>> futures = new ArrayList<>();
        int imageIndex = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            final boolean isVideo = file.getContentType() != null && file.getContentType().startsWith("video/");
            final boolean isMainImage = !isVideo && (imageIndex == 0);
            if (!isVideo) imageIndex++;

            CompletableFuture<CostumeImage> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String original = file.getOriginalFilename();
                    String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String folderName = costume.getId() != null ? String.valueOf(costume.getId()) : "new_" + System.currentTimeMillis();
                    String path = String.format("costumes/%s/%d_%s", folderName, System.currentTimeMillis(), safeName);

                    String mediaUrl = firebaseStorageService.uploadFile(file, path);

                    CostumeImage media = new CostumeImage();
                    media.setImageUrl(mediaUrl);
                    media.setType(isMainImage ? "MAIN" : "DETAIL");
                    media.setMediaType(isVideo ? "VIDEO" : "IMAGE");
                    media.setCostume(costume);
                    return media;
                } catch (Exception e) {
                    throw new RuntimeException("Lỗi upload media lên Cloud: " + e.getMessage(), e);
                }
            }, executor);

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<CostumeImage> future : futures) {
                costume.getImages().add(future.get());
            }
        } catch (Exception e) {
            throw new RuntimeException("Tiến trình upload media thất bại: " + e.getCause().getMessage(), e);
        } finally {
            executor.shutdown();
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
            throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
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
            throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
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
            throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
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
                .cost(costume.getCost())
                .depositAmount(costume.getDepositAmount())
                .rentDiscount(costume.getRentDiscount())
                .status(costume.getStatus())
                .providerId(costume.getProviderId())
                .completedRentCount(costume.getCompletedRentCount())
                .imageUrls(costume.getImages().stream().map(CostumeImage::getImageUrl).collect(Collectors.toList()))
                .medias(costume.getImages().stream()
                        .map(i -> CostumeResponse.MediaResponse.builder()
                                .url(i.getImageUrl())
                                .type(i.getType())
                                .mediaType(i.getMediaType() != null ? i.getMediaType() : "IMAGE")
                                .build())
                        .collect(Collectors.toList()))
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
                .characters(costume.getCharacters() != null ?
                        costume.getCharacters().stream().map(c -> CostumeResponse.CharacterDto.builder()
                                .id(c.getId())
                                .name(c.getName())
                                .anime(c.getAnime())
                                .build()).collect(Collectors.toList())
                        : new java.util.ArrayList<>())
                .gender(costume.getGender())
                .build();
    }

    private void validateCreateRequest(Integer currentUserId, CostumeRequest request) {
        if (request.getProviderId() == null || !providerRepository.existsById(request.getProviderId())) {
            throw new AppException(ErrorCode.PROVIDER_NOT_FOUND);
        }

        ensureCurrentUserOwnsProvider(currentUserId, request.getProviderId());

        if (!isValidString(request.getName())) throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
        if (request.getPricePerDay() == null || request.getPricePerDay().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
        }
        validateMediaLimits(request);

        boolean hasImage = (request.getImageFiles() != null && request.getImageFiles().stream().anyMatch(f -> f != null && !f.isEmpty())) ||
                           (request.getVideoFiles() != null && request.getVideoFiles().stream().anyMatch(f -> f != null && !f.isEmpty()));
        if (!hasImage) throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
        if (request.getRentDiscount() != null) {
            if (request.getRentDiscount() < 0 || request.getRentDiscount() > 100)
                throw new RuntimeException("rentDiscount must be between 0 and 100");
        }

        // Validate gender if provided
        if (request.getGender() != null && !request.getGender().trim().isEmpty()) {
            String g = request.getGender().trim().toUpperCase();
            List<String> allowed = Arrays.asList("MALE", "FEMALE", "UNISEX", "GENDERLESS");
            if (!allowed.contains(g)) throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
        }

        // Cross-field validation: if both cost and depositAmount provided, ensure cost >= depositAmount
        if (request.getCost() != null && request.getDepositAmount() != null) {
            if (request.getCost().compareTo(request.getDepositAmount()) < 0) {
                throw new AppException(ErrorCode.INVALID_COSTUME_REQUEST);
            }
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

    private void ensureCurrentUserOwnsProvider(Integer currentUserId, Integer providerId) {
        if (isPrivileged()) return;
        if (currentUserId == null) {
            throw new RuntimeException("Unauthorized");
        }
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));
        if (!currentUserId.equals(provider.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private void ensureCurrentUserOwnsCostume(Integer currentUserId, Costume costume) {
        if (isPrivileged()) return;
        if (currentUserId == null) {
            currentUserId = getCurrentUserIdFromContext();
        }
        if (currentUserId == null) {
            throw new RuntimeException("Unauthorized");
        }
        ensureCurrentUserOwnsProvider(currentUserId, costume.getProviderId());
    }

    // --- API SEARCH CHỦ ĐỘNG ---
    @Override
    public List<CostumeResponse> searchCostumes(String keyword) {
        List<Costume> costumes = costumeRepository.findByNameContainingIgnoreCaseAndStatusNot(keyword, "DELETED");

        return costumes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateMediaLimits(CostumeRequest request) {
        int imageCount = 0;
        int videoCount = 0;
        if (request.getImageFiles() != null) {
            for (MultipartFile file : request.getImageFiles()) {
                if (file != null && !file.isEmpty()) {
                    String contentType = file.getContentType();
                    if (contentType != null && contentType.startsWith("video/")) {
                        videoCount++;
                    } else {
                        imageCount++;
                    }
                }
            }
        }
        
        if (request.getVideoFiles() != null) {
            for (MultipartFile file : request.getVideoFiles()) {
                if (file != null && !file.isEmpty()) {
                    String contentType = file.getContentType();
                    if (contentType != null && contentType.startsWith("image/")) {
                        imageCount++;
                    } else {
                        videoCount++;
                    }
                }
            }
        }

        if (imageCount > 5) throw new RuntimeException("Chỉ được phép tải lên tối đa 5 hình ảnh.");
        if (videoCount > 1) throw new RuntimeException("Chỉ được phép tải lên tối đa 1 video.");
    }
}