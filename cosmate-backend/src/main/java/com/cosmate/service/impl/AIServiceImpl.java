package com.cosmate.service.impl;

import com.cosmate.configuration.AiKnowledgeBase;
import com.cosmate.configuration.AiModelRouter;
import com.cosmate.dto.request.CustomAnswerRequest;
import com.cosmate.dto.request.PoseFeedbackRequest;
import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.QuizSubmitRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.CustomAnswerResponse;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.CharacterRepository;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.PoseFeedbackRepository;
import com.cosmate.repository.PoseScoreRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.repository.StyleSurveyRepository;
import com.cosmate.repository.SystemConfigRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.repository.ReviewRepository;
import com.cosmate.service.AIService;
import com.cosmate.service.FirebaseStorageService;
import com.cosmate.service.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    private final CostumeImageRepository costumeImageRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final CostumeRepository costumeRepository;
    private final AiKnowledgeBase aiKnowledgeBase;
    private final PoseScoreRepository poseScoreRepository;
    private final FirebaseStorageService firebaseStorageService;
    private final NotificationService notificationService;
    private final OrderDetailRepository orderDetailRepository;
    private final ProviderRepository providerRepository;
    private final PoseFeedbackRepository poseFeedbackRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ConcurrentHashMap<String, List<Integer>> archetypeTopCache = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final StyleSurveyRepository styleSurveyRepository;
    private final AiModelRouter aiModelRouter;
    private final ReviewRepository reviewRepository;
    @Lazy
    @Autowired
    private AIService selfProxy;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${tryon.api.key}")
    private String tryonApiKey;

    @Value("${fal.api.key}")
    private String falApiKey;

    /**
     * Tìm kiếm các trang phục có độ tương đồng cao bằng thuật toán Cosine Similarity.
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request) {
        selfProxy.consumeTokens(getCurrentUserIdFromContext(), 15);
        try {
            String queryText = request.getText() != null ? request.getText().trim() : "";
            List<MultipartFile> imageFiles = request.getFiles();
            boolean hasImages = imageFiles != null && !imageFiles.isEmpty() && imageFiles.stream().anyMatch(file -> file != null && !file.isEmpty());
            boolean hasText = !queryText.isEmpty();

            if (!hasImages && !hasText) {
                throw new IllegalArgumentException("Tính năng tìm kiếm AI cần nhập text hoặc upload ít nhất 1 hình ảnh!");
            }

            List<Double> queryImageVector = null;
            if (hasImages) {
                String imageTags = extractFeaturesFromMultipleImages(imageFiles);
                queryImageVector = selfProxy.callGeminiGetVector(imageTags);
            }

            List<Double> queryTextVector = hasText ? selfProxy.callGeminiGetVector(queryText) : null;

            List<Costume> allCostumes = costumeRepository.findAllWithVector();
            List<SearchResponse> results = new ArrayList<>();

            for (Costume costume : allCostumes) {
                if (costume.getTextVector() == null || costume.getTextVector().isEmpty()) continue;
                if (hasImages && (costume.getImageVector() == null || costume.getImageVector().isEmpty())) continue;

                List<Double> dbTextVector = objectMapper.readValue(costume.getTextVector(), new TypeReference<List<Double>>() {});
                double textScore = queryTextVector != null ? calculateCosineSimilarity(queryTextVector, dbTextVector) : 0.0;

                double finalScore;

                if (hasImages && hasText) {
                    // TRƯỜNG HỢP 1: CÓ CẢ HÌNH VÀ CHỮ (Trọng số 70/30)

                    // 1. Tính điểm Hình ảnh
                    List<Double> dbImageVector = objectMapper.readValue(costume.getImageVector(), new TypeReference<List<Double>>() {});
                    double imageScore = calculateCosineSimilarity(queryImageVector, dbImageVector);

                    // 2. Nâng cấp điểm Chữ (Bí kíp của Khoan)
                    // - Lấy điểm so với Mô tả (đã tính ở ngoài vòng if)
                    // - Tính thêm điểm so với Tags Hình ảnh
                    double textScoreVsImageTags = calculateCosineSimilarity(queryTextVector, dbImageVector);

                    // -> Chọn ra điểm cao nhất
                    double bestTextScore = Math.max(textScore, textScoreVsImageTags);

                    // 3. Gộp lại
                    finalScore = (imageScore * 0.7) + (bestTextScore * 0.3);

                    if (finalScore <= 0.5) continue;

                } else if (hasImages) {
                    // TRƯỜNG HỢP 2: CHỈ CÓ HÌNH (Trọng số 100% hình)
                    List<Double> dbImageVector = objectMapper.readValue(costume.getImageVector(), new TypeReference<List<Double>>() {});
                    double imageScore = calculateCosineSimilarity(queryImageVector, dbImageVector);
                    finalScore = imageScore * 1.0;
                    if (finalScore <= 0.5) continue;

                } else {
                    // TRƯỜNG HỢP 3: CHỈ CÓ CHỮ
                    // Cho query của user đi so sánh với cả Text Mô tả VÀ Text Hình ảnh (Tags)
                    double imageScoreIfAny = 0.0;
                    if (costume.getImageVector() != null && !costume.getImageVector().isEmpty()) {
                        List<Double> dbImageVector = objectMapper.readValue(costume.getImageVector(), new TypeReference<List<Double>>() {});
                        imageScoreIfAny = calculateCosineSimilarity(queryTextVector, dbImageVector);
                    }

                    // Lấy điểm cao nhất giữa việc giống Mô tả hoặc giống Hình ảnh
                    finalScore = Math.max(textScore, imageScoreIfAny);

                    if (finalScore <= 0.5) continue;
                }

                String displayImageUrl = costume.getImages().isEmpty() ? "" : costume.getImages().get(0).getImageUrl();
                results.add(SearchResponse.builder()
                        .costumeId(costume.getId())
                        .costumeName(costume.getName())
                        .imageUrl(displayImageUrl)
                        .price(costume.getPricePerDay())
                        .similarityScore(finalScore)
                        .build());
            }

            return results.stream()
                    .sorted(Comparator.comparingDouble(SearchResponse::getSimilarityScore).reversed())
                    .limit(30)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Lỗi trong quá trình AI tìm kiếm Dual-Vector: {}", e.getMessage(), e);
            throw new RuntimeException("Tìm kiếm AI thất bại: " + e.getMessage());
        }
    }

    /**
     * Tạo vector nhúng (embedding) cho trang phục và lưu vào Database.
     */
    @Async
    @org.springframework.transaction.annotation.Transactional
    @Override
    public void generateAndSaveVector(Integer costumeId, boolean updateText, boolean updateImage) {
        if (!updateText && !updateImage) return; // Không cần cập nhật gì thì thoát luôn cho nhẹ server

        log.info("Chạy ngầm cập nhật Vector (Text: {}, Image: {}) cho Costume ID: {}", updateText, updateImage, costumeId);

        costumeRepository.findById(costumeId).ifPresent(costume -> {
            try {
                boolean isChanged = false;

                // 1. CHỈ TẠO LẠI IMAGE VECTOR NẾU CÓ YÊU CẦU
                if (updateImage && costume.getImages() != null && !costume.getImages().isEmpty()) {
                    List<byte[]> downloadedImages = new ArrayList<>();

                    // Giới hạn tối đa 3-5 ảnh để bóc tag (tránh payload quá lớn gây lỗi quá tải)
                    costume.getImages().stream().limit(4).forEach(img -> {
                        byte[] bytes = downloadImageFromUrl(img.getImageUrl());
                        if (bytes != null) downloadedImages.add(bytes);
                    });

                    if (!downloadedImages.isEmpty()) {
                        // GỌI API LẦN 1: Bóc tag cho toàn bộ 4 ảnh cùng lúc
                        String allImageTags = extractTagsFromMultipleImageBytes(downloadedImages);

                        if (allImageTags != null && !allImageTags.isEmpty()) {
                            // GỌI API LẦN 2: Nhúng chuỗi tag thành Vector
                            String imageVector = generateVectorForText(allImageTags);
                            if (imageVector != null) {
                                costume.setImageVector(imageVector);
                                isChanged = true;
                            }
                        }
                    }
                }

                // 2. CHỈ TẠO LẠI TEXT VECTOR NẾU CÓ YÊU CẦU
                if (updateText) {
                    String textInput = (costume.getName() + " " + (costume.getDescription() != null ? costume.getDescription() : "")).trim();
                    String textVector = generateVectorForText(textInput);
                    if (textVector != null) {
                        costume.setTextVector(textVector);
                        isChanged = true;
                    }
                }

                // 3. CHỈ LƯU VÀO DB NẾU THỰC SỰ CÓ SỰ THAY ĐỔI
                if (isChanged) {
                    costumeRepository.save(costume);
                    log.info("Hoàn tất cập nhật Vector cho bộ: {}", costume.getName());
                }

            } catch (Exception e) {
                log.error("Lỗi chạy ngầm Vector cho ID {}: {}", costumeId, e.getMessage());
            }
        });
    }

    private boolean isGenderMatch(String costumeGender, String preferredGender) {
        if (preferredGender == null || "ALL".equalsIgnoreCase(preferredGender)) {
            return true;
        }
        if (costumeGender == null || "UNISEX".equalsIgnoreCase(costumeGender) || "GENDERLESS".equalsIgnoreCase(costumeGender)) {
            return true;
        }
        if ("MALE".equalsIgnoreCase(preferredGender)) {
            return "MALE".equalsIgnoreCase(costumeGender);
        }
        if ("FEMALE".equalsIgnoreCase(preferredGender)) {
            return "FEMALE".equalsIgnoreCase(costumeGender);
        }
        return false;
    }

    /**
     * Phân tích hồ sơ người dùng và đưa ra gợi ý nhân vật Cosplay phù hợp.
     */
    @Override
    public List<SearchResponse> recommendCosplay(RecommendationRequest request) {
        try {
            // Lấy Archetype của user hiện tại
            User currentUser = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String && !auth.getPrincipal().equals("anonymousUser")) {
                currentUser = userRepository.findById(Integer.parseInt((String) auth.getPrincipal())).orElse(null);
            }
            String currentArchetype = currentUser != null ? currentUser.getCurrentArchetype() : null;

            // Đọc RAG lấy thông tin
            JsonNode archetypes = aiKnowledgeBase.getArchetypes();
            JsonNode targetArchetype = null;
            if (archetypes != null) {
                for (JsonNode node : archetypes) {
                    if (node.path("archetype_id").asText().equals(request.getArchetypeId())) {
                        targetArchetype = node;
                        break;
                    }
                }
            }
            if (targetArchetype == null) throw new RuntimeException("Không tìm thấy dữ liệu Archetype trong RAG!");

            String basePrompt = aiKnowledgeBase.getPromptRecommendation();

            String searchContent = basePrompt
                    .replace("{name}", targetArchetype.path("archetype_name").asText())
                    .replace("{desire}", targetArchetype.path("core_desire").asText())
                    .replace("{style}", targetArchetype.path("clothing_style").asText())
                    .replace("{color}", targetArchetype.path("color_palette").toString())
                    .replace("{characters}", targetArchetype.path("famousCharacters").toString());

            // TẦNG 1: LỌC CỘNG TÁC (TỪ CACHE RAM)
            List<Integer> cachedIds = currentArchetype != null ? archetypeTopCache.getOrDefault(currentArchetype, Collections.emptyList()) : Collections.emptyList();
            LinkedHashSet<Integer> candidateIds = new LinkedHashSet<>(cachedIds);

            // TẦNG 2: FALLBACK NHÂN VẬT (NẾU CACHE ĐỒ ÍT QUÁ)
            if (candidateIds.size() < 5 && !candidateIds.isEmpty()) {
                candidateIds.addAll(findCharacterFallbackIds(candidateIds));
            }

            // TẦNG 3: AI VECTOR FALLBACK (COLD START)
            if (candidateIds.size() < 5) {
                try {
                    List<Double> queryVector = selfProxy.callGeminiGetVector(searchContent);
                    List<Costume> vectorFallback = costumeRepository.findAllWithVector().stream()
                            .filter(c -> c.getTextVector() != null && !c.getTextVector().isEmpty())
                            .filter(c -> !candidateIds.contains(c.getId()))
                            .filter(c -> isGenderMatch(c.getGender(), request.getPreferredGender()))
                            .sorted((a, b) -> {
                                try {
                                    List<Double> vecA = objectMapper.readValue(a.getTextVector(), new TypeReference<List<Double>>() {});
                                    List<Double> vecB = objectMapper.readValue(b.getTextVector(), new TypeReference<List<Double>>() {});
                                    return Double.compare(calculateCosineSimilarity(queryVector, vecB), calculateCosineSimilarity(queryVector, vecA));
                                } catch (Exception e) { return 0; }
                            })
                            .limit(30).collect(Collectors.toList());
                    for (Costume costume : vectorFallback) candidateIds.add(costume.getId());
                } catch (Exception e) {
                    log.warn("Gemini Vector Fallback thất bại (Lỗi: {}). Sử dụng fallback bằng từ khóa nhân vật...", e.getMessage());
                    // Fallback: Tìm các trang phục khớp với tên các nhân vật tiêu biểu của Archetype
                    JsonNode famousCharacters = targetArchetype.path("famousCharacters");
                    if (famousCharacters != null && famousCharacters.isArray()) {
                        for (JsonNode charNode : famousCharacters) {
                            String charName = charNode.asText();
                            List<Costume> matchCostumes = costumeRepository.findByNameContainingIgnoreCaseAndStatusNot(charName, "DELETED");
                            for (Costume c : matchCostumes) {
                                if (isGenderMatch(c.getGender(), request.getPreferredGender())) {
                                    candidateIds.add(c.getId());
                                }
                            }
                        }
                    }
                    
                    // Nếu vẫn ít hơn 5 sản phẩm, lấy ngẫu nhiên một số trang phục có status AVAILABLE làm dự phòng cuối cùng
                    if (candidateIds.size() < 5) {
                        List<Costume> randomAvailable = costumeRepository.findAll().stream()
                                .filter(c -> !"DELETED".equals(c.getStatus()))
                                .filter(c -> isGenderMatch(c.getGender(), request.getPreferredGender()))
                                .limit(20)
                                .collect(Collectors.toList());
                        for (Costume c : randomAvailable) candidateIds.add(c.getId());
                    }
                }
            }

            List<Costume> costumes = costumeRepository.findAllById(new ArrayList<>(candidateIds));
            return costumes.stream()
                    .filter(c -> isGenderMatch(c.getGender(), request.getPreferredGender()))
                    .map(c -> SearchResponse.builder()
                            .costumeId(c.getId())
                            .costumeName(c.getName())
                            .imageUrl(c.getImages().isEmpty() ? "" : c.getImages().get(0).getImageUrl())
                            .price(c.getPricePerDay())
                            .similarityScore(0.80 + (new java.util.Random().nextDouble() * 0.19999))
                            .isCollaborative(cachedIds.contains(c.getId()))
                            .build())
                    .limit(30)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi Recommend AI: {}", e.getMessage(), e);
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.AI_SERVICE_OVERLOADED);
        }
    }

    // JOB CHẠY NGẦM MỖI 12 TIẾNG TÍNH TOÁN LỌC CỘNG TÁC
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 4 * * ?")
    public void refreshArchetypeCache() {
        try {
            log.info("Bắt đầu Refresh Archetype Cache...");
            Map<String, List<Integer>> cache = new HashMap<>();
            for (User user : userRepository.findAll()) {
                if (user.getCurrentArchetype() == null || user.getCurrentArchetype().isEmpty()) continue;
                List<Integer> topCostumeIds = orderDetailRepository.findTopCostumeIdsByArchetype(user.getCurrentArchetype(), 10);
                cache.put(user.getCurrentArchetype(), topCostumeIds);
            }
            archetypeTopCache.clear();
            archetypeTopCache.putAll(cache);
        } catch (Exception e) { log.error("Lỗi refresh cache: {}", e.getMessage()); }
    }

    // TÌM ĐỒ CÙNG NHÂN VẬT ĐỂ DỰ PHÒNG
    private List<Integer> findCharacterFallbackIds(Collection<Integer> costumeIds) {
        java.util.Set<Integer> characterIds = costumeRepository.findCharactersByCostumeIds(new ArrayList<>(costumeIds));
        if (characterIds.isEmpty()) return Collections.emptyList();
        return costumeRepository.findCostumeIdsByCharacterIds(new ArrayList<>(characterIds), new ArrayList<>(costumeIds));
    }

    /**
     * Kiểm duyệt nội dung hình ảnh để đảm bảo tuân thủ tiêu chuẩn cộng đồng (Kiểm tra 18+/Bạo lực).
     */

    @Override
    public void validateMultipleImageContents(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            // 1. Lấy prompt từ RAM cache
            String demoPrompt = aiKnowledgeBase.getPromptModDemo();

            partsNode.add(buildTextPart(demoPrompt));

            // 2. Dùng Helper thêm mảng Ảnh siêu gọn
            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, false);

            // 3. Dùng Helper lấy kết quả trả về siêu gọn
            String resultText = extractGeminiResponseText(response);

            if (resultText.toUpperCase().contains("UNSAFE")) {
                throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.AI_CONTENT_BLOCKED);
            }
        } catch (Exception e) {
            log.error("Lỗi khi kiểm duyệt AI hàng loạt: {}", e.getMessage());
            throw new RuntimeException("Lỗi kiểm duyệt ảnh: " + e.getMessage());
        }
    }

    @Override
    public String moderateCostumeImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return "UNSAFE_IRRELEVANT";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();
            String promptText = aiKnowledgeBase.getPromptModCostume();
            partsNode.add(buildTextPart(promptText));
            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }
            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));
            String result = extractGeminiResponseText(selfProxy.callGeminiGenerateContent(body, false));
            return result == null ? "UNSAFE_IRRELEVANT" : result.trim().toUpperCase();
        } catch (Exception e) {
            log.error("Lỗi kiểm duyệt ảnh costume: {}", e.getMessage(), e);
            return "ERROR";
        }
    }

    @Async
    @Override
    public void processNewCostumeAsync(Integer costumeId, List<MultipartFile> files) {
        Costume costume = costumeRepository.findById(costumeId).orElse(null);
        if (costume == null) return;

        String moderation = moderateCostumeImages(files);

        if ("ERROR".equals(moderation)) {
            log.warn("AI lỗi hoặc quá tải. Giữ nguyên trạng thái PENDING cho bộ đồ ID: {}", costumeId);
            return;
        }

        try {
            if ("UNSAFE_VIOLATION".equals(moderation)) {
                costume.setStatus("REJECTED");
                costumeRepository.save(costume);
                sendCostumeRejectionNotification(costume, "Sản phẩm bị từ chối do chứa hình ảnh vi phạm tiêu chuẩn cộng đồng (18+/Bạo lực).");
                return;
            }
            if ("UNSAFE_IRRELEVANT".equals(moderation)) {
                costume.setStatus("REJECTED");
                costumeRepository.save(costume);
                sendCostumeRejectionNotification(costume, "Sản phẩm bị từ chối do hình ảnh tải lên không liên quan đến trang phục hoặc phụ kiện Cosplay.");
                return;
            }
            selfProxy.generateAndSaveVector(costumeId, true, true);
            costume.setStatus("AVAILABLE");
            costumeRepository.save(costume);
        } catch (Exception e) {
            log.error("processNewCostumeAsync failed for {}: {}", costumeId, e.getMessage(), e);
        }
    }

    /**
     * Đánh giá tư thế (pose) cosplay so với nhân vật gốc và trả về điểm số kèm nhận xét.
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(noRollbackFor = IllegalArgumentException.class)
    public PoseScoringResponse scorePose(PoseScoringRequest request) {
        Integer currentUserId = getCurrentUserIdFromContext();
        selfProxy.consumeTokens(currentUserId, 20);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            // 1. Lấy Luật WCS Cứng
            String rules = "Bạn là một giám khảo Cosplay. Dưới đây là 50% tiêu chuẩn WCS chính thức:\n"
                    + aiKnowledgeBase.getWcsRules() + "\n";

            // 2. Lấy Luật Động từ Database (Nạp vào lúc runtime)
            String dynamicRules = systemConfigRepository.findById("DYNAMIC_POSE_RULES")
                    .map(SystemConfig::getConfigValue).orElse("");

            if (!dynamicRules.isEmpty()) {
                rules += "TIÊU CHUẨN CỘNG ĐỒNG (Chiếm tối đa 50% trọng số điểm): " + dynamicRules + "\n";
            }

            // 3. Lấy Prompt từ DB và map các chuỗi thay vì dùng String.format (%s) dễ gây lỗi
            String basePrompt = aiKnowledgeBase.getPromptScorePose();
            
            String referenceText = (request.getReferenceImage() != null && !request.getReferenceImage().isEmpty())
                    ? "Tôi có đính kèm 2 bức ảnh. Ảnh thứ hai là ảnh nhân vật gốc (Reference). Hãy chấm điểm dựa trên độ tương đồng về góc độ, tạo dáng và biểu cảm so với ảnh gốc này. "
                    : "Hãy dựa vào cơ sở dữ liệu của bạn về nhân vật này để chấm điểm. ";

            String finalPrompt = basePrompt
                    .replace("{characterName}", request.getCharacterName() != null ? request.getCharacterName() : "Unknown")
                    .replace("{referenceText}", referenceText);

            String promptText = rules + finalPrompt;

            partsNode.add(buildTextPart(promptText));

            // Gắn ảnh User
            ObjectNode userImagePart = buildImagePart(request.getImage());
            if (userImagePart != null) partsNode.add(userImagePart);

            // Gắn ảnh mẫu (nếu có)
            if (request.getReferenceImage() != null && !request.getReferenceImage().isEmpty()) {
                ObjectNode refImagePart = buildImagePart(request.getReferenceImage());
                if (refImagePart != null) partsNode.add(refImagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, true);
            String resultJson = extractGeminiResponseText(response);

            // ==========================================
            // XỬ LÝ JSON KẾT QUẢ VÀ BẮT LỖI AI
            // ==========================================

            // 1. Kiểm tra Safety Filter (Bị Google chặn do ảnh nhạy cảm/vũ khí)
            if (resultJson == null || resultJson.trim().isEmpty()) {
                throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.AI_CONTENT_BLOCKED);
            }

            // 2. Ép kiểu và lọc bỏ Markdown (ví dụ: ```json ... ```)
            int startIndex = resultJson.indexOf('{');
            int endIndex = resultJson.lastIndexOf('}');
            if (startIndex >= 0 && endIndex > startIndex) {
                resultJson = resultJson.substring(startIndex, endIndex + 1).trim();
            } else {
                // Nếu AI chỉ chat nhảm mà không nhả ra dấu ngoặc nhọn nào
                throw new RuntimeException("Hệ thống AI đang quá tải và không thể đưa ra điểm số chính xác lúc này. Bạn vui lòng bấm chấm điểm lại nhé!");
            }

            // 3. Đọc JSON an toàn (Tránh lỗi 500 khi AI thiếu dấu phẩy, ngoặc kép)
            JsonNode resultNode;
            try {
                resultNode = objectMapper.readTree(resultJson);
            } catch (Exception e) {
                log.error("Lỗi Parse JSON từ AI: {}", resultJson);
                throw new RuntimeException("Định dạng điểm số AI trả về bị lỗi. Bạn vui lòng thử lại!");
            }

            // 4. Kiểm tra xem AI có trả về đủ các trường không
            if (!resultNode.has("score") || !resultNode.has("comment")) {
                throw new RuntimeException("AI không chấm đủ điểm cho bạn. Vui lòng bấm thử lại!");
            }

            int finalScore = resultNode.path("score").asInt();
            String aiComment = resultNode.path("comment").asText();
            if ("NOT_COSPLAY".equalsIgnoreCase(aiComment)) {
                throw new IllegalArgumentException("Bé Mèo không nhận diện được người hoặc trang phục trong ảnh. Bạn thử ảnh khác nha!");
            }

            // ==========================================
            // PHẦN LƯU FIREBASE VÀ DB GIỮ NGUYÊN
            // ==========================================
            String originalName = request.getImage().getOriginalFilename();
            String safeName = originalName == null ? String.valueOf(System.currentTimeMillis()) : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String path = String.format("pose_battles/%d_%s", System.currentTimeMillis(), safeName);
            String uploadedImageUrl = firebaseStorageService.uploadFile(request.getImage(), path);

            PoseScore newScoreRecord = PoseScore.builder()
                    .cosplayerId(currentUserId)
                    .imageUrl(uploadedImageUrl)
                    .score(BigDecimal.valueOf(finalScore))
                    .comment(aiComment)
                    .characterName(request.getCharacterName())
                    .build();

            newScoreRecord = poseScoreRepository.save(newScoreRecord);

            return PoseScoringResponse.builder()
                    .id(newScoreRecord.getId())
                    .score(finalScore)
                    .comment(aiComment)
                    .characterName(newScoreRecord.getCharacterName())
                    .imageUrl(newScoreRecord.getImageUrl())
                    .build();

        } catch (Exception e) {
            log.error("AI chấm điểm Pose thất bại: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi chấm điểm Pose: " + e.getMessage());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void createPoseFeedback(PoseFeedbackRequest request) {
        Integer userId = getCurrentUserIdFromContext();
        if (userId == null) {
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.USER_NOT_FOUND));
        PoseScore poseScore = poseScoreRepository.findById(request.getPoseScoreId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy PoseScore tương ứng!"));

        boolean alreadySubmitted = poseFeedbackRepository.existsByUser_IdAndPoseScore_Id(userId, poseScore.getId());
        if (alreadySubmitted) {
            throw new RuntimeException("Bạn đã gửi phản hồi cho lượt chấm điểm này rồi!");
        }

        PoseFeedback feedback = PoseFeedback.builder()
                .user(user)
                .poseScore(poseScore)
                .feedbackText(request.getFeedbackText())
                .build();

        try {
            poseFeedbackRepository.save(feedback);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new RuntimeException("Bạn đã gửi phản hồi cho lượt chấm điểm này rồi!");
        }
    }

    // --- Các hàm tiện ích (Utility Methods) ---

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void consumeTokens(Integer userId, int amount) {
        if (userId == null) {
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.UNAUTHORIZED);
        }
        
        int updatedRows = userRepository.deductTokensSafe(userId, amount);
        if (updatedRows == 0) {
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.AI_TOKEN_INSUFFICIENT);
        }
    }

    private Integer getCurrentUserIdFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                if (((String) principal).equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf((String) principal);
            }
            if (principal instanceof Integer) return (Integer) principal;
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void sendCostumeRejectionNotification(Costume costume, String message) {
        try {
            if (costume.getProviderId() == null) return;
            // Lấy User ID từ Provider ID
            com.cosmate.entity.Provider provider = providerRepository.findById(costume.getProviderId()).orElse(null);
            if (provider == null || provider.getUserId() == null) return;

            com.cosmate.entity.Notification notification = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(provider.getUserId()).build())
                    .type("SYSTEM_WARNING")
                    .header("Sản phẩm bị từ chối phê duyệt")
                    .content(message + " Sản phẩm: " + costume.getName())
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();

            notificationService.create(notification);
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo từ chối Costume: {}", e.getMessage());
        }
    }

    private String extractJsonArray(String rawText) {
        if (rawText == null) return "[]";
        String text = rawText.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[[\\s\\S]*\\]").matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text;
    }

    // 1. HÀM CHÍNH SẼ ĐƯỢC GỌI
    // Nếu gặp lỗi 429 (TooManyRequests) hoặc 503 (Server Quá Tải), nó sẽ thử lại tối đa 3 lần.
    // Độ trễ lần lượt: 1s -> 2s -> 4s.
    @Retryable(
            retryFor = {org.springframework.web.client.HttpClientErrorException.class, org.springframework.web.client.HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Double> callGeminiGetVector(String text) {
        log.info("Đang gọi AI tạo Vector (Model Chính)...");
        return executeVectorCall(text, false);
    }

    // 2. HÀM FALLBACK (RECOVER)
    // Nếu hàm trên đã cố gắng 3 lần mà vẫn lỗi (hết Quota), Spring sẽ tự động nhảy vào đây.
    @Recover
    public List<Double> recoverVectorCall(Exception e, String text) {
        log.warn("Model chính thất bại sau 3 lần thử (Lỗi: {}). Kích hoạt Model Backup...", e.getMessage());
        return executeVectorCall(text, true); // Gọi lại với cờ isBackup = true
    }

    // 3. HÀM THỰC THI CHUNG
    private List<Double> executeVectorCall(String text, boolean isBackup) {
        String modelName = aiModelRouter.getEmbeddingModelName(isBackup);
        String url = aiModelRouter.buildUrl(modelName, "embedContent") + "?key=" + apiKey;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);

        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = objectMapper.createArrayNode();
        ObjectNode textPart = objectMapper.createObjectNode();

        textPart.put("text", text);
        parts.add(textPart);
        content.set("parts", parts);

        body.set("content", content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        // Dùng exchange hoặc postForEntity thay vì postForObject để Spring văng đúng Exception 4xx, 5xx
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);

        JsonNode valuesNode = response.getBody().path("embedding").path("values");
        List<Double> vector = new ArrayList<>();
        if (valuesNode != null && valuesNode.isArray()) {
            valuesNode.forEach(val -> vector.add(val.asDouble()));
        }
        return vector;
    }

    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size() || vectorA.isEmpty()) return 0.0;

        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }

        return (normA == 0 || normB == 0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public String generateVectorForText(String text) {
        try {
            // Sử dụng lại hàm callGeminiGetVector đã viết sẵn
            List<Double> vector = selfProxy.callGeminiGetVector(text);
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            log.error("Lỗi khi tạo vector nhúng (embedding) từ text: {}", e.getMessage());
            return null;
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * ?")
    @Override
    public void generateVectorsForMissingImages() {
        List<Costume> allCostumes = costumeRepository.findCostumesMissingVector();
        int successCount = 0;

        log.info("Bắt đầu tạo Dual-Vector chạy ngầm cho {} bộ đồ...", allCostumes.size());

        for (Costume costume : allCostumes) {
            try {
                boolean isChanged = false;

                // 1. CHỈ TẠO IMAGE VECTOR NẾU NÓ THỰC SỰ TRỐNG (DÙNG LOGIC GỘP ẢNH TỐI ƯU API)
                if (costume.getImageVector() == null || costume.getImageVector().trim().isEmpty()) {
                    if (costume.getImages() != null && !costume.getImages().isEmpty()) {
                        List<byte[]> downloadedImages = new ArrayList<>();

                        // Lấy tối đa 4 ảnh của RIÊNG bộ đồ này
                        costume.getImages().stream().limit(4).forEach(img -> {
                            byte[] bytes = downloadImageFromUrl(img.getImageUrl());
                            if (bytes != null) downloadedImages.add(bytes);
                        });

                        if (!downloadedImages.isEmpty()) {
                            // GỌI API LẦN 1: Bóc tag gom chung cho tất cả ảnh của bộ đồ này
                            String allImageTags = extractTagsFromMultipleImageBytes(downloadedImages);

                            if (allImageTags != null && !allImageTags.isEmpty()) {
                                // GỌI API LẦN 2: Nhúng chuỗi tag thành Vector
                                String imageVector = generateVectorForText(allImageTags);
                                if (imageVector != null) {
                                    costume.setImageVector(imageVector);
                                    isChanged = true;
                                }
                            }
                        }
                    }
                }

                // 2. CHỈ TẠO TEXT VECTOR NẾU NÓ THỰC SỰ TRỐNG
                if (costume.getTextVector() == null || costume.getTextVector().trim().isEmpty()) {
                    String textInput = ((costume.getName() != null ? costume.getName() : "") + " " + (costume.getDescription() != null ? costume.getDescription() : "")).trim();
                    if (!textInput.isEmpty()) {
                        String textVector = generateVectorForText(textInput);
                        if (textVector != null) {
                            costume.setTextVector(textVector);
                            isChanged = true;
                        }
                    }
                }

                // 3. LƯU VÀO DB NẾU CÓ CẬP NHẬT
                if (isChanged) {
                    costumeRepository.save(costume);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Lỗi tạo vector cho Costume {}: {}", costume.getId(), e.getMessage());
            }
        }
        log.info("Hoàn tất! Đã nâng cấp {} bộ đồ lên Dual-Vector.", successCount);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String generateCostumeDescription(String costumeName, Integer personaId, List<MultipartFile> files) {
        selfProxy.consumeTokens(getCurrentUserIdFromContext(), 20);
        if (files == null || files.isEmpty()) return "Không thể generate được description, vui lòng tả thủ công.";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            String promptStr = "Bạn là một chuyên gia về trang phục cosplay. ";
            if (costumeName != null && !costumeName.trim().isEmpty()) {
                promptStr += "Tên nhân vật/bộ trang phục này là: '" + costumeName + "'. ";
            }

            String personaKey = "PROMPT_PERSONA_SALE";
            if (personaId != null) {
                if (personaId == 2) {
                    personaKey = "PROMPT_PERSONA_CUTE";
                } else if (personaId == 3) {
                    personaKey = "PROMPT_PERSONA_DEEP";
                }
            }
            String personaPrompt = systemConfigRepository.findById(personaKey)
                    .map(SystemConfig::getConfigValue)
                    .filter(value -> value != null && !value.trim().isEmpty())
                    .orElseGet(() -> systemConfigRepository.findById("PROMPT_PERSONA_SALE")
                            .map(SystemConfig::getConfigValue)
                            .orElse(""));
            promptStr += personaPrompt + " ";
            promptStr += "Hãy nhìn vào những hình ảnh đính kèm và tạo ra một đoạn mô tả. ";
            promptStr += " QUAN TRỌNG: TUYỆT ĐỐI KHÔNG chào hỏi, KHÔNG xưng hô (không dùng từ bạn hay tôi). KHÔNG có câu mở bài hay kết luận. CHỈ TRẢ VỀ trực tiếp nội dung mô tả sản phẩm để gắn thẳng lên website.";

            partsNode.add(buildTextPart(promptStr));

            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, false);
            String resultText = extractGeminiResponseText(response);

            return resultText.isEmpty() ? "Không thể generate được description, vui lòng tả thủ công." : resultText.replaceAll("[*#_]", "");
        } catch (Exception e) {
            log.error("Lỗi khi AI generate description: {}", e.getMessage());
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.AI_SERVICE_OVERLOADED);
        }
    }

    public String extractFeaturesFromMultipleImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return "";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            String promptText = aiKnowledgeBase.getPromptTagsMulti();
            partsNode.add(buildTextPart(promptText));

            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, false);
            return extractGeminiResponseText(response);
        } catch (Exception e) {
            log.error("Lỗi khi trích xuất đặc điểm từ nhiều ảnh: {}", e.getMessage());
            return "";
        }
    }

    private byte[] downloadImageFromUrl(String imageUrl) {
        try {
            // Dùng RestTemplate tải ảnh về dưới dạng mảng byte
            return restTemplate.getForObject(imageUrl, byte[].class);
        } catch (Exception e) {
            log.error("Không thể tải ảnh từ URL: {}. Lỗi: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    private String extractTagsFromBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return "";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            String promptText = aiKnowledgeBase.getPromptTagsSingle();
            partsNode.add(buildTextPart(promptText));

            ObjectNode imagePart = buildImagePart(imageBytes, "image/jpeg");
            if (imagePart != null) partsNode.add(imagePart);

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, false);
            return extractGeminiResponseText(response);
        } catch (Exception e) {
            log.error("Lỗi AI khi bóc tag từ mảng byte: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public List<SearchResponse> fallbackSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList(); // Nếu user không nhập chữ gì thì trả về rỗng
        }

        try {
            // Tìm kiếm cơ bản trong Database (giống hệt API search thường của ông)
            List<Costume> costumes = costumeRepository.findByNameContainingIgnoreCaseAndStatusNot(keyword.trim(), "DELETED");

            return costumes.stream().map(c -> {
                String displayImageUrl = c.getImages().isEmpty() ? "" : c.getImages().get(0).getImageUrl();

                return SearchResponse.builder()
                        .costumeId(c.getId())
                        .costumeName(c.getName())
                        .imageUrl(displayImageUrl)
                        .price(c.getPricePerDay())
                        .similarityScore(0.0) // AI tèo rồi nên điểm tương đồng = 0
                        .build();
            }).limit(10).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi cả tìm kiếm dự phòng: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<PoseScore> getMyPoseHistory(Integer userId, String keyword) {
        if (userId == null) return Collections.emptyList();

        if (keyword != null && !keyword.trim().isEmpty()) {
            return poseScoreRepository.findByCosplayerIdAndCharacterNameContainingIgnoreCaseOrderByCreatedAtDesc(userId, keyword.trim());
        }
        return poseScoreRepository.findByCosplayerIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public PoseScore getPoseScoreDetail(Integer id, Integer currentUserId) {
        return poseScoreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kết quả chấm điểm này!"));
    }

    @Override
    public PoseScore updatePoseCharacterName(Integer scoreId, Integer userId, String newName) {
        PoseScore score = poseScoreRepository.findById(scoreId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kết quả chấm điểm này!"));

        // Bảo mật: Kiểm tra xem bài chấm này có phải của user đang request không
        if (!score.getCosplayerId().equals(userId)) {
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.FORBIDDEN);
        }

        score.setCharacterName(newName);
        return poseScoreRepository.save(score);
    }

    @Override
    public void deletePoseScore(Integer scoreId, Integer userId) {
        PoseScore score = poseScoreRepository.findById(scoreId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kết quả chấm điểm này!"));

        if (!score.getCosplayerId().equals(userId)) {
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.FORBIDDEN);
        }

        poseScoreRepository.delete(score);
    }

    @Override
    public List<CustomAnswerResponse> analyzeCustomAnswersBatch(List<CustomAnswerRequest> requests) {
        if (requests == null || requests.isEmpty()) return Collections.emptyList();

        try {
            ArrayNode inputJsonArray = objectMapper.createArrayNode();
            for (int i = 0; i < requests.size(); i++) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("id", i);
                item.put("question", requests.get(i).getQuestionContext());
                item.put("answer", requests.get(i).getUserAnswer());
                inputJsonArray.add(item);
            }

            String basePrompt = aiKnowledgeBase.getPromptAnalyzeAnswers();
            
            String promptText = basePrompt.replace("{answers}", inputJsonArray.toString());

            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            partsNode.add(buildTextPart(promptText));
            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, true);
            String resultJson = extractGeminiResponseText(response);

            resultJson = extractJsonArray(resultJson);

            // ==========================================
            // LOGIC MAPPING JSON SANG ĐỐI TƯỢNG GIỮ NGUYÊN
            // ==========================================
            JsonNode resultNodeArray = objectMapper.readTree(resultJson);
            List<CustomAnswerResponse> finalResponses = new ArrayList<>(Collections.nCopies(requests.size(), null));

            if (resultNodeArray.isArray()) {
                for (JsonNode node : resultNodeArray) {
                    int id = node.path("id").asInt(-1);
                    if (id >= 0 && id < requests.size()) {
                        boolean isValid = node.path("isValid").asBoolean();
                        String reason = node.path("reason").asText();
                        Map<String, Integer> scores = null;

                        if (isValid && node.has("scores")) {
                            scores = objectMapper.convertValue(node.path("scores"), new TypeReference<Map<String, Integer>>() {});
                        }

                        finalResponses.set(id, CustomAnswerResponse.builder()
                                .isValid(isValid)
                                .reason(reason)
                                .scores(scores)
                                .build());
                    }
                }
            }

            for (int i = 0; i < finalResponses.size(); i++) {
                if (finalResponses.get(i) == null) {
                    finalResponses.set(i, CustomAnswerResponse.builder()
                            .isValid(false)
                            .reason("AI từ chối phân tích hoặc xảy ra lỗi bỏ sót.")
                            .build());
                }
            }
            return finalResponses;
        } catch (Exception e) {
            log.error("Lỗi khi phân tích câu trả lời Custom Batch: {}", e.getMessage(), e);
            throw new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.AI_SERVICE_OVERLOADED);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String submitStyleQuiz(Integer userId, QuizSubmitRequest request) {
        selfProxy.consumeTokens(userId != null ? userId : getCurrentUserIdFromContext(), 30);
        int totalE = 0, totalA = 0, totalO = 0;

        // 1. Cộng điểm trắc nghiệm tĩnh
        for (com.cosmate.dto.request.QuizSubmitRequest.StaticAnswer ans : request.getStaticAnswers()) {
            totalE += ans.getScoreE();
            totalA += ans.getScoreA();
            totalO += ans.getScoreO();
        }

        // 2. Chấm điểm tự luận bằng AI
        if (request.getCustomAnswers() != null && !request.getCustomAnswers().isEmpty()) {
            List<CustomAnswerResponse> aiResults = analyzeCustomAnswersBatch(request.getCustomAnswers());
            for (CustomAnswerResponse res : aiResults) {
                if (res.isValid() && res.getScores() != null) {
                    totalE += res.getScores().getOrDefault("E", 0);
                    totalA += res.getScores().getOrDefault("A", 0);
                    totalO += res.getScores().getOrDefault("O", 0);
                }
            }
        }

        // 3. Tính toán Archetype bằng Euclid
        String finalArchetypeId = calculateClosestArchetype(totalE, totalA, totalO);

        // 4. CẬP NHẬT DATABASE: Cột current_archetype trong bảng Users
        User user = userRepository.findById(userId).orElseThrow(() -> new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.USER_NOT_FOUND));
        user.setCurrentArchetype(finalArchetypeId);
        userRepository.save(user);

        // 5. LƯU DATABASE: Bảng Style_Surveys
        try {
            String jsonAnswers = objectMapper.writeValueAsString(request);
            com.cosmate.entity.StyleSurvey survey = com.cosmate.entity.StyleSurvey.builder()
                    .cosplayerId(user)
                    .answersJson(jsonAnswers)
                    .recommendedTags(finalArchetypeId)
                    .build();
            styleSurveyRepository.save(survey);
        } catch (Exception e) {
            log.error("Lỗi lưu kết quả khảo sát: {}", e.getMessage());
        }

        return finalArchetypeId;
    }

    private String calculateClosestArchetype(int userE, int userA, int userO) {
        String bestArchetype = "ARCH_12";
        double minDistance = Double.MAX_VALUE;

        for (java.util.Map.Entry<String, int[]> entry : aiKnowledgeBase.getArchetypeCoordinates().entrySet()) {
            int[] coord = entry.getValue();
            // Công thức Euclid: sqrt((E2-E1)^2 + (A2-A1)^2 + (O2-O1)^2)
            double distance = Math.sqrt(Math.pow(userE - coord[0], 2) + Math.pow(userA - coord[1], 2) + Math.pow(userO - coord[2], 2));
            if (distance < minDistance) {
                minDistance = distance;
                bestArchetype = entry.getKey();
            }
        }
        return bestArchetype;
    }

    // =========================================================================
    // CỤM HÀM GENERATE CONTENT (CHO TEXT, JSON, CHẤM ĐIỂM CÓ HỖ TRỢ RETRY)
    // =========================================================================

    @Retryable(
            retryFor = {org.springframework.web.client.HttpClientErrorException.class, org.springframework.web.client.HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public JsonNode callGeminiGenerateContent(ObjectNode body, boolean isComplexTask) {
        log.info("Đang gọi AI Generate Content (isComplexTask: {})...", isComplexTask);
        return executeGenerateCall(body, isComplexTask, false);
    }

    @Recover
    public JsonNode recoverGenerateCall(Exception e, ObjectNode body, boolean isComplexTask) {
        log.warn("Model AI text chính thất bại (Lỗi: {}). Chuyển sang Model Dự phòng...", e.getMessage());
        return executeGenerateCall(body, isComplexTask, true);
    }

    private JsonNode executeGenerateCall(ObjectNode body, boolean isComplexTask, boolean isBackup) {
        // Tự động điều hướng: Tác vụ khó (Pose, Quiz) thì gọi model xịn, tác vụ dễ (Tag, Mô tả) thì gọi model nhanh
        String modelName = isComplexTask ? aiModelRouter.getReasoningModelName(isBackup) : aiModelRouter.getFastModelName(isBackup);
        String url = aiModelRouter.buildUrl(modelName, "generateContent") + "?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            return response.getBody();
        } catch (Exception e) {
            // Nếu model dự phòng 1 (Gemini 3.1 Flash Lite) của tác vụ phức tạp bị lỗi, chuyển tiếp sang model dự phòng cuối cùng (Gemini 3 Flash)
            if (isBackup && isComplexTask) {
                log.warn("Model dự phòng 1 ({}) thất bại. Thử gọi model dự phòng cuối cùng (models/gemini-3-flash)...", modelName);
                try {
                    String fallbackUrl = aiModelRouter.buildUrl("models/gemini-3-flash", "generateContent") + "?key=" + apiKey;
                    ResponseEntity<JsonNode> response = restTemplate.postForEntity(fallbackUrl, entity, JsonNode.class);
                    return response.getBody();
                } catch (Exception ex) {
                    log.error("Cả 3 model của tác vụ phức tạp đều thất bại: {}", ex.getMessage());
                    throw ex;
                }
            }
            throw e;
        }
    }

    // =========================================================================
    // CÁC HÀM TIỆN ÍCH (HELPER METHODS) ĐỂ TRÁNH LẶP CODE
    // =========================================================================

    /**
     * Helper: Đóng gói text thành JSON Part chuẩn của Gemini
     */
    private ObjectNode buildTextPart(String text) {
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("text", text);
        return textPart;
    }

    /**
     * Helper: Chuyển MultipartFile thành Base64 JSON Part chuẩn của Gemini
     */
    private ObjectNode buildImagePart(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return null;
            String base64Image = java.util.Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Image);

            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.set("inline_data", inlineData);
            return imagePart;
        } catch (Exception e) {
            log.error("Lỗi khi convert ảnh sang Base64: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Helper: Bóc tách chuỗi kết quả từ JSON Response của Gemini
     */
    private String extractGeminiResponseText(JsonNode response) {
        if (response != null && response.has("candidates") && !response.path("candidates").isEmpty()) {
            return response.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();
        }
        return "";
    }

    /**
     * Helper: Chuyển mảng byte (byte[]) thành Base64 JSON Part chuẩn của Gemini
     */
    private ObjectNode buildImagePart(byte[] imageBytes, String mimeType) {
        try {
            if (imageBytes == null || imageBytes.length == 0) return null;
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mime_type", mimeType != null ? mimeType : "image/jpeg");
            inlineData.put("data", base64Image);

            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.set("inline_data", inlineData);
            return imagePart;
        } catch (Exception e) {
            log.error("Lỗi khi convert mảng byte sang Base64: {}", e.getMessage());
            return null;
        }
    }

    /**
     * TỐI ƯU API: Bóc tag từ một danh sách các ảnh (byte[]) trong CÙNG 1 REQUEST
     */
    private String extractTagsFromMultipleImageBytes(List<byte[]> imagesBytes) {
        if (imagesBytes == null || imagesBytes.isEmpty()) return "";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            String promptText = aiKnowledgeBase.getPromptTagsMulti();
            partsNode.add(buildTextPart(promptText));

            for (byte[] bytes : imagesBytes) {
                ObjectNode imagePart = buildImagePart(bytes, "image/jpeg");
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, false); // Gọi model Flash nhanh
            return extractGeminiResponseText(response);
        } catch (Exception e) {
            log.error("Lỗi AI khi bóc tag từ nhiều mảng byte: {}", e.getMessage());
            return "";
        }
    }

    // =========================================================================
    // PHASE 2: DYNAMIC RAG - TỰ HỌC TỪ FEEDBACK CỦA CỘNG ĐỒNG
    // Chạy ngầm vào 2h sáng Chủ Nhật hàng tuần
    // =========================================================================
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * SUN")
    public void analyzeAndApplyPoseFeedbacks() {
        log.info("Bắt đầu tiến trình AI phân tích Pose Feedback từ cộng đồng...");
        List<PoseFeedback> feedbacks = poseFeedbackRepository.findByStatus("PENDING");

        if (feedbacks.isEmpty()) {
            log.info("Chưa có feedback nào mới. Bỏ qua.");
            return;
        }

        try {
            ArrayNode feedbackArray = objectMapper.createArrayNode();
            for (PoseFeedback fb : feedbacks) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("userId", fb.getUser().getId()); // Đưa userId vào để AI biết đếm số lượng người (chặn spam)
                node.put("feedback", fb.getFeedbackText());
                feedbackArray.add(node);
            }

            // PROMPT THẦN THÁNH: Ép AI làm toán và gom cụm
            String basePrompt = aiKnowledgeBase.getPromptAnalyzeFeedback();
            
            String promptText = basePrompt.replace("{feedback}", feedbackArray.toString());

            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();
            partsNode.add(buildTextPart(promptText));
            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            // Gọi model suy luận sâu
            JsonNode response = selfProxy.callGeminiGenerateContent(body, true);
            String resultJson = extractGeminiResponseText(response);
            resultJson = extractJsonArray(resultJson); // Tái sử dụng hàm để cắt lấy chuỗi JSON

            JsonNode resultNode = objectMapper.readTree(resultJson);
            String supplementaryRules = resultNode.path("supplementary_rules").asText();

            if (!supplementaryRules.isEmpty()) {
                // Lưu vào Database để dùng cho những lần chấm điểm sau
                SystemConfig dynamicConfig = systemConfigRepository.findById("DYNAMIC_POSE_RULES")
                        .orElse(SystemConfig.builder()
                                .configKey("DYNAMIC_POSE_RULES")
                                .description("Luật chấm điểm bổ sung tự học từ Pose Feedback")
                                .build());

                dynamicConfig.setConfigValue(supplementaryRules);
                systemConfigRepository.save(dynamicConfig);
                log.info("Đã nạp thành công Luật Bổ Sung vào Database!");
            }

            for (PoseFeedback fb : feedbacks) {
                fb.setStatus("PROCESSED");
            }
            poseFeedbackRepository.saveAll(feedbacks);

        } catch (Exception e) {
            log.error("Lỗi khi AI phân tích Pose Feedback: {}", e.getMessage());
        }
    }

    @Async
    @Override
    public void analyzeReviewAsync(Integer reviewId, String comment) {
        if (comment == null || comment.trim().isEmpty()) return;
        try {
            String promptText = aiKnowledgeBase.getPromptAnalyzeReview();
            
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();
            partsNode.add(buildTextPart(promptText + "\nNội dung đánh giá: " + comment));
            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = selfProxy.callGeminiGenerateContent(body, false);
            String resultJson = extractGeminiResponseText(response);
            
            int startIndex = resultJson.indexOf('{');
            int endIndex = resultJson.lastIndexOf('}');
            if (startIndex >= 0 && endIndex > startIndex) {
                resultJson = resultJson.substring(startIndex, endIndex + 1).trim();
                JsonNode resultNode = objectMapper.readTree(resultJson);
                
                String sentiment = resultNode.path("sentiment").asText("NEUTRAL");
                boolean isToxic = resultNode.path("is_toxic").asBoolean(false);
                String summary = resultNode.path("summary").asText("");
                
                reviewRepository.findById(reviewId).ifPresent(review -> {
                    review.setAiSentiment(sentiment.toUpperCase());
                    review.setIsSpamOrToxic(isToxic);
                    review.setAiSummary(summary);
                    reviewRepository.save(review);
                    
                    if (review.getOrder() != null && review.getOrder().getProviderId() != null) {
                        Integer providerId = review.getOrder().getProviderId();
                        List<Review> validReviews = reviewRepository.findByOrderProviderId(providerId)
                                .stream()
                                .filter(r -> r.getIsSpamOrToxic() == null || !r.getIsSpamOrToxic())
                                .collect(Collectors.toList());
                                
                        int totalReviews = validReviews.size();
                        final double average = totalReviews > 0
                                ? validReviews.stream().mapToDouble(Review::getRating).sum() / totalReviews
                                : 0.0;
                        
                        providerRepository.findById(providerId).ifPresent(provider -> {
                            provider.setTotalReviews(totalReviews);
                            provider.setTotalRating(java.math.BigDecimal.valueOf(average));
                            providerRepository.save(provider);
                        });
                    }
                });
            }
        } catch (Exception e) {
            log.error("Lỗi khi AI phân tích review id {}: {}", reviewId, e.getMessage());
        }
    }

    @Override
    public com.cosmate.dto.response.ArchetypeStatsResponse getArchetypeStats(String archetypeId) {
        int count = userRepository.countByCurrentArchetype(archetypeId);
        return com.cosmate.dto.response.ArchetypeStatsResponse.builder()
                .archetypeId(archetypeId)
                .totalUsers(count)
                .build();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String generateVirtualTryOn(Integer costumeId, String garmentImageUrl, MultipartFile personImage, String provider) {
        String activeProvider = provider != null ? provider.trim().toUpperCase() : "FAL";
        
        // Đồng bộ trừ 50 token cho mọi provider
        selfProxy.consumeTokens(getCurrentUserIdFromContext(), 50);
        
        log.info("Bắt đầu thực hiện Virtual Try-On cho Costume ID: {} với Garment Image URL: {} qua Provider: {}", costumeId, garmentImageUrl, activeProvider);
        
        // 1. Tìm Costume theo costumeId (để kiểm tra hợp lệ)
        costumeRepository.findById(costumeId)
                .orElseThrow(() -> new com.cosmate.exception.AppException(com.cosmate.exception.ErrorCode.COSTUME_NOT_FOUND));
        
        if (garmentImageUrl == null || garmentImageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Đường dẫn ảnh trang phục không được rỗng!");
        }
        log.info("Sử dụng garment_image_url thành công: {}", garmentImageUrl);
        
        // 2. Upload ảnh personImage của user lên Firebase
        if (personImage == null || personImage.isEmpty()) {
            throw new IllegalArgumentException("Ảnh người dùng không được rỗng!");
        }
        
        String humanImageUrl;
        try {
            String originalName = personImage.getOriginalFilename();
            String safeName = originalName == null ? String.valueOf(System.currentTimeMillis()) : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String path = String.format("vto/%d_%s", System.currentTimeMillis(), safeName);
            humanImageUrl = firebaseStorageService.uploadFile(personImage, path);
            log.info("Upload human_image lên Firebase thành công: {}", humanImageUrl);
        } catch (Exception e) {
            log.error("Lỗi khi upload ảnh người dùng lên Firebase: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tải ảnh người dùng lên Firebase: " + e.getMessage());
        }
        
        // 3. Rẽ nhánh gọi nhà cung cấp API
        if ("TRYON".equals(activeProvider)) {
            return executeTryOnLabsAsync(personImage, garmentImageUrl);
        } else {
            // Fal.ai
            try {
                String url = "https://fal.run/fal-ai/idm-vton";
                
                // Set Headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Key " + falApiKey);
                
                // Set Body
                ObjectNode body = objectMapper.createObjectNode();
                body.put("human_image_url", humanImageUrl);
                body.put("garment_image_url", garmentImageUrl);
                body.put("description", "Cosplay costume");
                body.put("category", "upper_body");
                
                log.info("Gửi request POST tới Fal.ai VTO API: {}", body.toString());
                HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
                
                ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
                JsonNode responseNode = response.getBody();
                log.info("Nhận phản hồi thành công từ Fal.ai VTO API: {}", responseNode);
                
                if (responseNode == null) {
                    throw new RuntimeException("Không nhận được phản hồi (null) từ Fal.ai VTO API!");
                }
                
                // Parse JSON response:
                String outputUrl = null;
                if (responseNode.has("image") && responseNode.path("image").has("url")) {
                    outputUrl = responseNode.path("image").path("url").asText();
                } else if (responseNode.has("images") && responseNode.path("images").isArray() && !responseNode.path("images").isEmpty()) {
                    JsonNode firstImage = responseNode.path("images").get(0);
                    if (firstImage.isObject() && firstImage.has("url")) {
                        outputUrl = firstImage.path("url").asText();
                    } else {
                        outputUrl = firstImage.asText();
                    }
                }
                
                if (outputUrl == null || outputUrl.trim().isEmpty()) {
                    throw new RuntimeException("Không tìm thấy trường URL hình ảnh kết quả từ phản hồi của Fal.ai!");
                }
                
                log.info("Thực hiện Fal.ai Virtual Try-On thành công! URL ảnh kết quả: {}", outputUrl);
                return outputUrl;
                
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.error("Lỗi HTTP khi gọi Fal.ai VTO API: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                throw new RuntimeException("Gọi Fal.ai API thất bại: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Lỗi trong quá trình xử lý Virtual Try-On với Fal.ai: {}", e.getMessage(), e);
                throw new RuntimeException("Thực hiện Fal.ai Virtual Try-On thất bại: " + e.getMessage());
            }
        }
    }

    private String executeTryOnLabsAsync(MultipartFile personImage, String garmentImageUrl) {
        log.info("Bắt đầu thực hiện TryOn Labs bất đồng bộ với Multipart file và tải garment image.");
        try {
            // 1.1 Upload human image (Multipart form-data)
            HttpHeaders humanHeaders = new HttpHeaders();
            humanHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            humanHeaders.set("Authorization", "Bearer " + tryonApiKey);

            org.springframework.util.MultiValueMap<String, Object> humanBody = new org.springframework.util.LinkedMultiValueMap<>();
            
            String originalName = personImage.getOriginalFilename();
            String filename = (originalName == null || originalName.trim().isEmpty()) ? "human.jpg" : originalName;
            NamedByteArrayResource humanResource = new NamedByteArrayResource(personImage.getBytes(), filename);
            
            humanBody.add("image", humanResource);
            humanBody.add("type", "model");
            humanBody.add("gender", "female");

            log.info("TryOn Labs Bước 1.1: Upload human image (model) vật lý... Filename: {}", filename);
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> humanEntity = new HttpEntity<>(humanBody, humanHeaders);
            
            ResponseEntity<JsonNode> humanResponse = restTemplate.postForEntity(
                "https://prod.server.tryonlabs.ai/api/v1/experiment_image/", 
                humanEntity, 
                JsonNode.class
            );
            
            JsonNode humanResNode = humanResponse.getBody();
            String modelId = null;
            if (humanResNode != null) {
                if (humanResNode.has("id")) {
                    modelId = humanResNode.path("id").asText();
                } else if (humanResNode.has("image_id")) {
                    modelId = humanResNode.path("image_id").asText();
                }
            }
            log.info("TryOn Labs Bước 1.1 thành công. modelId: {}", modelId);
            
            // 1.2 Download garmentImageUrl and upload (Multipart form-data)
            log.info("TryOn Labs Bước 1.2: Đang tải ảnh garment từ URL: {}", garmentImageUrl);
            byte[] garmentBytes;
            try {
                garmentBytes = restTemplate.getForObject(garmentImageUrl, byte[].class);
                if (garmentBytes == null || garmentBytes.length == 0) {
                    throw new RuntimeException("Không tải được dữ liệu hình ảnh (0 bytes) từ URL: " + garmentImageUrl);
                }
                log.info("Tải ảnh garment thành công. Kích thước: {} bytes", garmentBytes.length);
            } catch (Exception e) {
                log.error("Lỗi khi tải ảnh garment từ URL: {}", e.getMessage(), e);
                throw new RuntimeException("Không thể tải ảnh trang phục từ URL: " + e.getMessage());
            }

            HttpHeaders garmentHeaders = new HttpHeaders();
            garmentHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            garmentHeaders.set("Authorization", "Bearer " + tryonApiKey);

            org.springframework.util.MultiValueMap<String, Object> garmentBody = new org.springframework.util.LinkedMultiValueMap<>();
            
            NamedByteArrayResource garmentResource = new NamedByteArrayResource(garmentBytes, "garment.jpg");
            garmentBody.add("image", garmentResource);
            garmentBody.add("type", "garment");

            log.info("TryOn Labs Bước 1.2: Upload garment image vật lý...");
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> garmentEntity = new HttpEntity<>(garmentBody, garmentHeaders);
            
            ResponseEntity<JsonNode> garmentResponse = restTemplate.postForEntity(
                "https://prod.server.tryonlabs.ai/api/v1/experiment_image/", 
                garmentEntity, 
                JsonNode.class
            );
            
            JsonNode garmentResNode = garmentResponse.getBody();
            String garmentId = null;
            if (garmentResNode != null) {
                if (garmentResNode.has("id")) {
                    garmentId = garmentResNode.path("id").asText();
                } else if (garmentResNode.has("image_id")) {
                    garmentId = garmentResNode.path("image_id").asText();
                }
            }
            log.info("TryOn Labs Bước 1.2 thành công. garmentId: {}", garmentId);
            
            // Kiểm tra ID hợp lệ
            if (modelId == null || modelId.trim().isEmpty() || garmentId == null || garmentId.trim().isEmpty()) {
                throw new RuntimeException("Không thể trích xuất ID ảnh từ TryOn Labs");
            }
            log.info("Lấy thành công ID từ TryOn: modelId={}, garmentId={}", modelId, garmentId);

            // Bước 2: POST tới https://prod.server.tryonlabs.ai/api/v1/experiment/ để bắt đầu ghép đồ
            HttpHeaders expHeaders = new HttpHeaders();
            expHeaders.setContentType(MediaType.APPLICATION_JSON);
            expHeaders.set("Authorization", "Bearer " + tryonApiKey);

            ObjectNode expBody = objectMapper.createObjectNode();
            expBody.put("person_id", modelId);
            expBody.put("garment_id", garmentId);
            expBody.put("action", "virtual_try_on");
            
            log.info("TryOn Labs Bước 2: Gửi request ghép đồ: {}", expBody.toString());
            HttpEntity<String> expEntity = new HttpEntity<>(expBody.toString(), expHeaders);
            ResponseEntity<JsonNode> expResponse = restTemplate.postForEntity(
                "https://prod.server.tryonlabs.ai/api/v1/experiment/", 
                expEntity, 
                JsonNode.class
            );
            
            JsonNode expResNode = expResponse.getBody();
            String experimentId = null;
            if (expResNode != null) {
                if (expResNode.has("id")) {
                    experimentId = expResNode.path("id").asText();
                } else if (expResNode.has("experiment_id")) {
                    experimentId = expResNode.path("experiment_id").asText();
                } else if (expResNode.has("experiment")) {
                    JsonNode expNode = expResNode.path("experiment");
                    if (expNode.has("id")) {
                        experimentId = expNode.path("id").asText();
                    } else if (expNode.has("experiment_id")) {
                        experimentId = expNode.path("experiment_id").asText();
                    }
                }
            }
            if (experimentId == null || experimentId.trim().isEmpty()) {
                throw new RuntimeException("Khởi tạo tiến trình ghép đồ TryOn Labs thất bại: " + expResNode);
            }
            log.info("TryOn Labs Bước 2 thành công. experimentId: {}", experimentId);
            
            // Bước 3: Polling kiểm tra trạng thái mỗi 3 giây, tối đa 100 lần (300 giây)
            String statusUrl = "https://prod.server.tryonlabs.ai/api/v1/experiment/" + experimentId + "/";
            int maxAttempts = 100;
            int attempt = 0;
            
            HttpHeaders getHeaders = new HttpHeaders();
            getHeaders.set("Authorization", "Bearer " + tryonApiKey);
            
            while (attempt < maxAttempts) {
                attempt++;
                log.info("TryOn Labs Bước 3: Polling lần {}/{}", attempt, maxAttempts);
                
                HttpEntity<Void> getEntity = new HttpEntity<>(getHeaders);
                ResponseEntity<JsonNode> getResponse = restTemplate.exchange(
                    statusUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    getEntity, 
                    JsonNode.class
                );
                
                JsonNode getResNode = getResponse.getBody();
                if (getResNode == null) {
                    throw new RuntimeException("Không nhận được phản hồi từ tiến trình TryOn Labs!");
                }
                
                String experimentStatus = "";
                if (getResNode.has("experiment")) {
                    experimentStatus = getResNode.path("experiment").path("status").asText("").toLowerCase();
                } else {
                    experimentStatus = getResNode.path("status").asText("").toLowerCase();
                }
                log.info("Trạng thái tiến trình TryOn Labs: experimentStatus={}", experimentStatus);
                
                if ("success".equals(experimentStatus) || "completed".equals(experimentStatus)) {
                    // Trích xuất URL từ mọi nguồn có thể
                    String outputUrl = null;
                    
                    // 1. Kiểm tra đối tượng experiment lồng nhau trước
                    if (getResNode.has("experiment")) {
                        JsonNode expNode = getResNode.path("experiment");
                        if (expNode.has("result_image_url")) {
                            outputUrl = expNode.path("result_image_url").asText();
                        } else if (expNode.has("output_url")) {
                            outputUrl = expNode.path("output_url").asText();
                        } else if (expNode.has("result_url")) {
                            outputUrl = expNode.path("result_url").asText();
                        } else if (expNode.has("url")) {
                            outputUrl = expNode.path("url").asText();
                        } else if (expNode.has("image_url")) {
                            outputUrl = expNode.path("image_url").asText();
                        } else if (expNode.has("result")) {
                            JsonNode resField = expNode.path("result");
                            if (resField.isTextual()) {
                                outputUrl = resField.asText();
                            } else if (resField.has("url")) {
                                outputUrl = resField.path("url").asText();
                            } else if (resField.has("image_url")) {
                                outputUrl = resField.path("image_url").asText();
                            }
                        } else if (expNode.has("output")) {
                            JsonNode outField = expNode.path("output");
                            if (outField.isTextual()) {
                                outputUrl = outField.asText();
                            } else if (outField.has("url")) {
                                outputUrl = outField.path("url").asText();
                            } else if (outField.has("image_url")) {
                                outputUrl = outField.path("image_url").asText();
                            }
                        }
                    }
                    
                    // 2. Nếu chưa tìm thấy, kiểm tra ở cấp root
                    if (outputUrl == null || outputUrl.trim().isEmpty()) {
                        if (getResNode.has("result_image_url")) {
                            outputUrl = getResNode.path("result_image_url").asText();
                        } else if (getResNode.has("output_url")) {
                            outputUrl = getResNode.path("output_url").asText();
                        } else if (getResNode.has("result_url")) {
                            outputUrl = getResNode.path("result_url").asText();
                        } else if (getResNode.has("url")) {
                            outputUrl = getResNode.path("url").asText();
                        } else if (getResNode.has("image_url")) {
                            outputUrl = getResNode.path("image_url").asText();
                        } else if (getResNode.has("result")) {
                            JsonNode resField = getResNode.path("result");
                            if (resField.isTextual()) {
                                outputUrl = resField.asText();
                            } else if (resField.has("url")) {
                                outputUrl = resField.path("url").asText();
                            } else if (resField.has("image_url")) {
                                outputUrl = resField.path("image_url").asText();
                            }
                        } else if (getResNode.has("output")) {
                            JsonNode outField = getResNode.path("output");
                            if (outField.isTextual()) {
                                outputUrl = outField.asText();
                            } else if (outField.has("url")) {
                                outputUrl = outField.path("url").asText();
                            } else if (outField.has("image_url")) {
                                outputUrl = outField.path("image_url").asText();
                            }
                        }
                    }
                    
                    // 3. Kiểm tra mảng images hoặc results (ở root hoặc lồng)
                    if (outputUrl == null || outputUrl.trim().isEmpty()) {
                        JsonNode imagesArr = null;
                        if (getResNode.has("images") && getResNode.path("images").isArray() && !getResNode.path("images").isEmpty()) {
                            imagesArr = getResNode.path("images");
                        } else if (getResNode.has("results") && getResNode.path("results").isArray() && !getResNode.path("results").isEmpty()) {
                            imagesArr = getResNode.path("results");
                        } else if (getResNode.has("experiment")) {
                            JsonNode expNode = getResNode.path("experiment");
                            if (expNode.has("images") && expNode.path("images").isArray() && !expNode.path("images").isEmpty()) {
                                imagesArr = expNode.path("images");
                            } else if (expNode.has("results") && expNode.path("results").isArray() && !expNode.path("results").isEmpty()) {
                                imagesArr = expNode.path("results");
                            }
                        }
                        
                        if (imagesArr != null) {
                            JsonNode firstImg = imagesArr.get(0);
                            if (firstImg.isObject()) {
                                if (firstImg.has("url")) {
                                    outputUrl = firstImg.path("url").asText();
                                } else if (firstImg.has("image_url")) {
                                    outputUrl = firstImg.path("image_url").asText();
                                }
                            } else {
                                outputUrl = firstImg.asText();
                            }
                        }
                    }
                    
                    if (outputUrl == null || outputUrl.trim().isEmpty()) {
                        throw new RuntimeException("Ghép đồ thành công nhưng không tìm thấy URL kết quả trong phản hồi!");
                    }
                    
                    log.info("TryOn Labs hoàn thành thành công sau {} lần thử! URL: {}", attempt, outputUrl);
                    return outputUrl;
                } else if ("failed".equals(experimentStatus) || "error".equals(experimentStatus)) {
                    throw new RuntimeException("TryOn Labs báo lỗi tiến trình ghép đồ!");
                }
                
                // Sleep 3 seconds
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Tiến trình polling bị gián đoạn: " + ie.getMessage());
                }
            }
            
            throw new java.util.concurrent.TimeoutException("Thời gian ghép đồ TryOn Labs vượt quá 300 giây. Vui lòng thử lại sau!");
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Lỗi HTTP khi gọi TryOn Labs API: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Gọi TryOn Labs API thất bại: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Lỗi trong quá trình polling TryOn Labs: {}", e.getMessage(), e);
            throw new RuntimeException("Thực hiện Virtual Try-On TryOn Labs thất bại: " + e.getMessage());
        }
    }

    private static class NamedByteArrayResource extends org.springframework.core.io.ByteArrayResource {
        private final String filename;
        
        public NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }
        
        @Override
        public String getFilename() {
            return this.filename;
        }
    }
}