package com.cosmate.service.impl;

import com.cosmate.configuration.AiKnowledgeBase;
import com.cosmate.configuration.AiModelRouter;
import com.cosmate.dto.request.CustomAnswerRequest;
import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.CustomAnswerResponse;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeImage;
import com.cosmate.entity.PoseScore;
import com.cosmate.entity.User;
import com.cosmate.configuration.AiKnowledgeBase;
import com.cosmate.repository.CharacterRepository;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.PoseScoreRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.AIService;
import com.cosmate.service.FirebaseStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

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
    private final OrderDetailRepository orderDetailRepository;
    private final CharacterRepository characterRepository;
    private final ConcurrentHashMap<String, List<Integer>> archetypeTopCache = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final AiModelRouter aiModelRouter; // Thêm vào danh sách inject của Lombok @RequiredArgsConstructor

    @Value("${gemini.api.key}")
    private String apiKey;

    /**
     * Tìm kiếm các trang phục có độ tương đồng cao bằng thuật toán Cosine Similarity.
     */
    @Override
    public List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request) {
        try {
            String queryText = request.getText() != null ? request.getText().trim() : "";
            List<MultipartFile> imageFiles = request.getFiles();

            if (imageFiles == null || imageFiles.isEmpty() || imageFiles.get(0).isEmpty()) {
                throw new IllegalArgumentException("Tính năng tìm kiếm bằng AI bắt buộc phải upload ít nhất 1 hình ảnh!");
            }

            // 1. TẠO VECTOR CHO ẢNH
            String imageTags = extractFeaturesFromMultipleImages(imageFiles);
            List<Double> queryImageVector = callGeminiGetVector(imageTags);

            // 2. TẠO VECTOR CHO CHỮ (NẾU CÓ)
            List<Double> queryTextVector = queryText.isEmpty() ? null : callGeminiGetVector(queryText);

            List<Costume> allCostumes = costumeRepository.findAllWithVector();
            List<SearchResponse> results = new ArrayList<>();

            for (Costume costume : allCostumes) {
                if (costume.getImageVector() == null || costume.getTextVector() == null || costume.getImageVector().isEmpty()) continue;

                List<Double> dbImageVector = objectMapper.readValue(costume.getImageVector(), new TypeReference<List<Double>>() {});
                List<Double> dbTextVector = objectMapper.readValue(costume.getTextVector(), new TypeReference<List<Double>>() {});

                double imageScore = calculateCosineSimilarity(queryImageVector, dbImageVector);
                double textScore = queryTextVector != null ? calculateCosineSimilarity(queryTextVector, dbTextVector) : 0.0;

                // 3. CÔNG THỨC 70% ẢNH - 30% CHỮ
                double finalScore = queryTextVector != null ? (imageScore * 0.7) + (textScore * 0.3) : imageScore;

                if (finalScore > 0.55) { // Ngưỡng an toàn
                    String displayImageUrl = costume.getImages().isEmpty() ? "" : costume.getImages().get(0).getImageUrl();
                    results.add(SearchResponse.builder()
                            .costumeId(costume.getId())
                            .costumeName(costume.getName())
                            .imageUrl(displayImageUrl)
                            .price(costume.getPricePerDay())
                            .similarityScore(finalScore)
                            .build());
                }
            }

            return results.stream()
                    .sorted(Comparator.comparingDouble(SearchResponse::getSimilarityScore).reversed())
                    .limit(10)
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
    @Override
    public void generateAndSaveVector(Integer costumeId, boolean updateText, boolean updateImage) {
        if (!updateText && !updateImage) return; // Không cần cập nhật gì thì thoát luôn cho nhẹ server

        log.info("Chạy ngầm cập nhật Vector (Text: {}, Image: {}) cho Costume ID: {}", updateText, updateImage, costumeId);

        costumeRepository.findById(costumeId).ifPresent(costume -> {
            try {
                boolean isChanged = false;

                // 1. CHỈ TẠO LẠI IMAGE VECTOR NẾU CÓ YÊU CẦU
                if (updateImage && costume.getImages() != null) {
                    StringBuilder allImageTags = new StringBuilder();
                    for (CostumeImage img : costume.getImages()) {
                        byte[] bytes = downloadImageFromUrl(img.getImageUrl());
                        if (bytes != null) {
                            String tags = extractTagsFromBytes(bytes);
                            if (!tags.isEmpty()) {
                                if (allImageTags.length() > 0) allImageTags.append(", ");
                                allImageTags.append(tags);
                            }
                        }
                    }
                    String imageVector = generateVectorForText(allImageTags.toString());
                    if (imageVector != null) {
                        costume.setImageVector(imageVector);
                        isChanged = true;
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

            String searchContent = String.format("Trang phục dành cho nguyên mẫu %s. Phong cách: %s. Màu: %s.",
                    targetArchetype.path("archetype_name").asText(),
                    targetArchetype.path("clothing_style").asText(),
                    targetArchetype.path("color_palette").toString());

            // TẦNG 1: LỌC CỘNG TÁC (TỪ CACHE RAM)
            List<Integer> cachedIds = currentArchetype != null ? archetypeTopCache.getOrDefault(currentArchetype, Collections.emptyList()) : Collections.emptyList();
            LinkedHashSet<Integer> candidateIds = new LinkedHashSet<>(cachedIds);

            // TẦNG 2: FALLBACK NHÂN VẬT (NẾU CACHE ĐỒ ÍT QUÁ)
            if (candidateIds.size() < 5 && !candidateIds.isEmpty()) {
                candidateIds.addAll(findCharacterFallbackIds(candidateIds));
            }

            // TẦNG 3: AI VECTOR FALLBACK (COLD START)
            if (candidateIds.size() < 5) {
                List<Double> queryVector = callGeminiGetVector(searchContent);
                List<Costume> vectorFallback = costumeRepository.findAllWithVector().stream()
                        .filter(c -> c.getTextVector() != null && !c.getTextVector().isEmpty())
                        .filter(c -> !candidateIds.contains(c.getId()))
                        .sorted((a, b) -> {
                            try {
                                List<Double> vecA = objectMapper.readValue(a.getTextVector(), new TypeReference<List<Double>>() {});
                                List<Double> vecB = objectMapper.readValue(b.getTextVector(), new TypeReference<List<Double>>() {});
                                return Double.compare(calculateCosineSimilarity(queryVector, vecB), calculateCosineSimilarity(queryVector, vecA));
                            } catch (Exception e) { return 0; }
                        })
                        .limit(30).collect(Collectors.toList());
                for (Costume costume : vectorFallback) candidateIds.add(costume.getId());
            }

            List<Costume> costumes = costumeRepository.findAllById(new ArrayList<>(candidateIds));
            return costumes.stream().map(c -> SearchResponse.builder()
                    .costumeId(c.getId())
                    .costumeName(c.getName())
                    .imageUrl(c.getImages().isEmpty() ? "" : c.getImages().get(0).getImageUrl())
                    .price(c.getPricePerDay())
                    .similarityScore(1.0).build()).limit(30).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi Recommend AI: {}", e.getMessage(), e);
            throw new RuntimeException("Gợi ý thất bại: " + e.getMessage());
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

            // 1. Dùng Helper thêm Text siêu gọn
            partsNode.add(buildTextPart("Bạn là một AI kiểm duyệt nội dung. Hãy kiểm tra... Trả về SAFE hoặc UNSAFE."));

            // 2. Dùng Helper thêm mảng Ảnh siêu gọn
            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = callGeminiGenerateContent(body, false);

            // 3. Dùng Helper lấy kết quả trả về siêu gọn
            String resultText = extractGeminiResponseText(response);

            if (resultText.toUpperCase().contains("UNSAFE")) {
                throw new RuntimeException("Hệ thống phát hiện hình ảnh vi phạm tiêu chuẩn cộng đồng!");
            }
        } catch (Exception e) {
            log.error("Lỗi khi kiểm duyệt AI hàng loạt: {}", e.getMessage());
            throw new RuntimeException("Lỗi kiểm duyệt ảnh: " + e.getMessage());
        }
    }

    /**
     * Đánh giá tư thế (pose) cosplay so với nhân vật gốc và trả về điểm số kèm nhận xét.
     */
    @Override
    public PoseScoringResponse scorePose(PoseScoringRequest request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            String promptText = "Bạn là một giám khảo Cosplay. Dưới đây là bộ tiêu chuẩn WCS chính thức:\n"
                    + aiKnowledgeBase.getWcsRules() + "\n"
                    + "Hãy so sánh ảnh user chụp với nhân vật '" + request.getCharacterName() + "'. ";

            if (request.getReferenceImage() != null && !request.getReferenceImage().isEmpty()) {
                promptText += "Tôi có đính kèm 2 bức ảnh. Ảnh thứ hai là ảnh nhân vật gốc (Reference). Hãy chấm điểm dựa trên độ tương đồng về góc độ, tạo dáng và biểu cảm so với ảnh gốc này. ";
            } else {
                promptText += "Hãy dựa vào cơ sở dữ liệu của bạn về nhân vật này để chấm điểm. ";
            }
            promptText += "Chỉ trả về JSON định dạng: {\"score\": [Điểm tổng 1-100], \"pose_score\": [1-40], \"expression_score\": [1-40], \"costume_score\": [1-20], \"comment\": \"[Nhận xét kỹ thuật...]\"}";

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

            JsonNode response = callGeminiGenerateContent(body, true);
            String resultJson = extractGeminiResponseText(response);

            // ==========================================
            // XỬ LÝ JSON KẾT QUẢ
            // ==========================================
            int startIndex = resultJson.indexOf('{');
            int endIndex = resultJson.lastIndexOf('}');
            if (startIndex >= 0 && endIndex > startIndex) {
                resultJson = resultJson.substring(startIndex, endIndex + 1).trim();
            }

            JsonNode resultNode = objectMapper.readTree(resultJson);
            int finalScore = resultNode.path("score").asInt();
            String aiComment = resultNode.path("comment").asText();

            // ==========================================
            // PHẦN LƯU FIREBASE VÀ DB GIỮ NGUYÊN
            // ==========================================
            String originalName = request.getImage().getOriginalFilename();
            String safeName = originalName == null ? String.valueOf(System.currentTimeMillis()) : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String path = String.format("pose_battles/%d_%s", System.currentTimeMillis(), safeName);
            String uploadedImageUrl = firebaseStorageService.uploadFile(request.getImage(), path);

            Integer currentUserId = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof String && !authentication.getPrincipal().equals("anonymousUser")) {
                try {
                    currentUserId = Integer.parseInt((String) authentication.getPrincipal());
                } catch (NumberFormatException e) {
                    log.warn("Không thể parse userId từ JWT: {}", authentication.getPrincipal());
                }
            }

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

    // --- Các hàm tiện ích (Utility Methods) ---

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
            retryFor = {HttpClientErrorException.TooManyRequests.class, HttpServerErrorException.class},
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
            List<Double> vector = callGeminiGetVector(text);
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

                // 1. CHỈ TẠO IMAGE VECTOR NẾU NÓ THỰC SỰ TRỐNG (TIẾT KIỆM REQUEST)
                if (costume.getImageVector() == null || costume.getImageVector().trim().isEmpty()) {
                    StringBuilder imageTags = new StringBuilder();
                    if (costume.getImages() != null && !costume.getImages().isEmpty()) {
                        costume.getImages().stream().limit(3).forEach(img -> {
                            byte[] imageBytes = downloadImageFromUrl(img.getImageUrl());
                            if (imageBytes != null) {
                                String hiddenTags = extractTagsFromBytes(imageBytes);
                                if (!hiddenTags.isEmpty()) {
                                    if (!imageTags.isEmpty()) imageTags.append(", ");
                                    imageTags.append(hiddenTags);
                                }
                            }
                        });
                    }
                    if (!imageTags.isEmpty()) {
                        String imageVector = generateVectorForText(imageTags.toString());
                        if (imageVector != null) {
                            costume.setImageVector(imageVector);
                            isChanged = true;
                        }
                    }
                }

                // 2. CHỈ TẠO TEXT VECTOR NẾU NÓ THỰC SỰ TRỐNG (TIẾT KIỆM REQUEST)
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

                // 3. CHỈ GỌI LỆNH SAVE VÀO DB NẾU THỰC SỰ CÓ CẬP NHẬT MỚI
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
    public String generateCostumeDescription(String costumeName, String customPrompt, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return "Không thể generate được description, vui lòng tả thủ công.";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            String promptStr = "Bạn là một chuyên gia về trang phục cosplay. ";
            if (costumeName != null && !costumeName.trim().isEmpty()) {
                promptStr += "Tên nhân vật/bộ trang phục này là: '" + costumeName + "'. ";
            }
            promptStr += "Hãy nhìn vào những hình ảnh đính kèm và tạo ra một đoạn mô tả. ";
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                promptStr += "ĐẶC BIỆT LƯU Ý YÊU CẦU SAU TỪ NGƯỜI DÙNG: " + customPrompt + ". ";
            }
            promptStr += " QUAN TRỌNG: TUYỆT ĐỐI KHÔNG chào hỏi, KHÔNG xưng hô (không dùng từ bạn hay tôi). KHÔNG có câu mở bài hay kết luận. CHỈ TRẢ VỀ trực tiếp nội dung mô tả sản phẩm để gắn thẳng lên website.";

            partsNode.add(buildTextPart(promptStr));

            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = callGeminiGenerateContent(body, false);
            String resultText = extractGeminiResponseText(response);

            return resultText.isEmpty() ? "Không thể generate được description, vui lòng tả thủ công." : resultText.replaceAll("[*#_]", "");
        } catch (Exception e) {
            log.error("Lỗi khi AI generate description: {}", e.getMessage());
            throw new RuntimeException("Hệ thống AI của Google đang quá tải (503). Bạn vui lòng đợi vài phút rồi thử bấm lại nha!");
        }
    }

    public String extractFeaturesFromMultipleImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return "";
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            partsNode.add(buildTextPart("Trích xuất các từ khóa đặc trưng nhất của bộ trang phục xuất hiện trong TẤT CẢ các bức ảnh đính kèm. Tập trung vào: loại trang phục, màu sắc, họa tiết, và tên nhân vật. Chỉ trả về một chuỗi các từ khóa ngăn cách bằng dấu phẩy."));

            for (MultipartFile file : files) {
                ObjectNode imagePart = buildImagePart(file);
                if (imagePart != null) partsNode.add(imagePart);
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = callGeminiGenerateContent(body, false);
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

            partsNode.add(buildTextPart("Hãy phân tích hình ảnh bộ trang phục này và liệt kê các từ khóa (tags) đặc trưng nhất. Tập trung vào: loại trang phục, màu sắc, họa tiết, chất liệu, phong cách, và tên nhân vật nếu nhận diện được. Chỉ trả về chuỗi các từ khóa ngăn cách bằng dấu phẩy, không giải thích dài dòng."));

            ObjectNode imagePart = buildImagePart(imageBytes, "image/jpeg");
            if (imagePart != null) partsNode.add(imagePart);

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = callGeminiGenerateContent(body, false);
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
    public PoseScore updatePoseCharacterName(Integer scoreId, Integer userId, String newName) {
        PoseScore score = poseScoreRepository.findById(scoreId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kết quả chấm điểm này!"));

        // Bảo mật: Kiểm tra xem bài chấm này có phải của user đang request không
        if (!score.getCosplayerId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền sửa kết quả của người khác!");
        }

        score.setCharacterName(newName);
        return poseScoreRepository.save(score);
    }

    @Override
    public void deletePoseScore(Integer scoreId, Integer userId) {
        PoseScore score = poseScoreRepository.findById(scoreId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kết quả chấm điểm này!"));

        if (!score.getCosplayerId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền xóa kết quả của người khác!");
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

            String promptText = "You are an expert psychological evaluator trained on Pennebaker LIWC and Big Five trait analysis.\n"
                    + "Tôi sẽ cung cấp một mảng JSON chứa các câu trả lời tự luận của người dùng.\n"
                    + "ĐẦU VÀO:\n" + inputJsonArray.toString() + "\n\n"
                    + "NHIỆM VỤ BẮT BUỘC:\n"
                    + "1. Bạn PHẢI đánh giá TẤT CẢ các phần tử trong mảng. Đầu vào có bao nhiêu ID, đầu ra BẮT BUỘC phải có bấy nhiêu ID tương ứng.\n"
                    + "2. Với mỗi câu trả lời, kiểm tra tính hợp lệ. Nếu vô nghĩa, chửi thề, đánh giá isValid = false.\n"
                    + "3. Nếu hợp lệ, chấm điểm E, A, O từ -2 đến 2 dựa trên Linguistic markers.\n\n"
                    + "ĐỊNH DẠNG ĐẦU RA BẮT BUỘC:\n"
                    + "Chỉ trả về MỘT MẢNG JSON duy nhất, không kèm giải thích, không dùng markdown. Cấu trúc mỗi phần tử:\n"
                    + "{\"id\": [id tương ứng], \"isValid\": true, \"reason\": \"Lý do ngắn\", \"scores\": {\"E\": 0, \"A\": 0, \"O\": 0}}";

            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            partsNode.add(buildTextPart(promptText));
            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            JsonNode response = callGeminiGenerateContent(body, true);
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
            throw new RuntimeException("Lỗi phân tích AI: " + e.getMessage());
        }
    }

    // Inject thêm cái này ở đầu file
    private final com.cosmate.repository.StyleSurveyRepository styleSurveyRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String submitStyleQuiz(Integer userId, com.cosmate.dto.request.QuizSubmitRequest request) {
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
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
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
            retryFor = {HttpClientErrorException.TooManyRequests.class, HttpServerErrorException.class},
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

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
        return response.getBody();
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
}