package com.cosmate.service.impl;

import com.cosmate.configuration.AiKnowledgeBase;
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
import com.cosmate.repository.*;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    private final CostumeImageRepository costumeImageRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final CostumeRepository costumeRepository;
    private final AiKnowledgeBase aiKnowledgeBase;
    private final PoseScoreRepository poseScoreRepository;
    private final FirebaseStorageService firebaseStorageService;
    private final OrderDetailRepository orderDetailRepository;
    private final CharacterRepository characterRepository;
    private final ConcurrentHashMap<String, List<Integer>> archetypeTopCache = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    @Value("${gemini.api.key}")
    private String apiKey;

    // Các hằng số cho Model AI của Google
    private static final String EMBEDDING_MODEL_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent";
    private static final String GENERATION_MODEL_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";
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
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 12 * 60 * 60 * 1000L)
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
            // [FIXED] Nối đúng URL và API Key
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;

            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            // 1. Tạo Prompt dùng chung cho toàn bộ danh sách ảnh
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("text", "Bạn là một AI kiểm duyệt nội dung. Hãy kiểm tra toàn bộ các bức ảnh được đính kèm này. Nếu CÓ BẤT KỲ bức ảnh nào chứa nội dung 18+, bạo lực, máu me, hoặc vi phạm tiêu chuẩn cộng đồng, hãy trả về CHÍNH XÁC một chữ: 'UNSAFE'. Nếu TẤT CẢ đều an toàn, trả về 'SAFE'.");
            partsNode.add(textPart);

            // 2. Mã hóa toàn bộ ảnh sang Base64 và gộp chung vào 1 Request
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                String base64Image = java.util.Base64.getEncoder().encodeToString(file.getBytes());
                String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

                ObjectNode inlineData = objectMapper.createObjectNode();
                inlineData.put("mime_type", mimeType);
                inlineData.put("data", base64Image);

                ObjectNode imagePart = objectMapper.createObjectNode();
                imagePart.set("inline_data", inlineData);
                partsNode.add(imagePart);
            }

            // 3. Xây dựng payload JSON
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.set("parts", partsNode);
            ArrayNode contentsArray = objectMapper.createArrayNode();
            contentsArray.add(contentNode);
            body.set("contents", contentsArray);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            // 4. Bắn request sang Gemini và LƯU LẠI RESPONSE ĐỂ KIỂM TRA
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            // 5. Kiểm tra xem nó có chửi thề không
            if (response != null && response.has("candidates")) {
                String resultText = response.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText().trim();

                if (resultText.toUpperCase().contains("UNSAFE")) {
                    throw new RuntimeException("Hệ thống phát hiện hình ảnh vi phạm tiêu chuẩn cộng đồng!");
                }
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
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;

            List<Object> partsList = new ArrayList<>();

            // 1. Nhồi Prompt text vào
            String promptText = "Bạn là một giám khảo Cosplay. Dưới đây là bộ tiêu chuẩn WCS chính thức:\n"
                    + aiKnowledgeBase.getWcsRules() + "\n"
                    + "Hãy so sánh ảnh user chụp với nhân vật '" + request.getCharacterName() + "'. ";

            if (request.getReferenceImage() != null && !request.getReferenceImage().isEmpty()) {
                promptText += "Tôi có đính kèm 2 bức ảnh. Ảnh thứ hai là ảnh nhân vật gốc (Reference). Hãy chấm điểm dựa trên độ tương đồng về góc độ, tạo dáng và biểu cảm so với ảnh gốc này. ";
            } else {
                promptText += "Hãy dựa vào cơ sở dữ liệu của bạn về nhân vật này để chấm điểm. ";
            }

            promptText += "Chỉ trả về JSON định dạng: {\"score\": [Điểm tổng 1-100], \"pose_score\": [1-40], \"expression_score\": [1-40], \"costume_score\": [1-20], \"comment\": \"[Nhận xét kỹ thuật...]\"}";
            partsList.add(Map.of("text", promptText));

            // 2. Nhồi ảnh User (Bắt buộc)
            String base64Image = java.util.Base64.getEncoder().encodeToString(request.getImage().getBytes());
            partsList.add(Map.of("inline_data", Map.of(
                    "mime_type", request.getImage().getContentType() != null ? request.getImage().getContentType() : "image/jpeg",
                    "data", base64Image
            )));

            // 3. Nhồi ảnh Mẫu (Nếu có)
            if (request.getReferenceImage() != null && !request.getReferenceImage().isEmpty()) {
                String base64RefImage = java.util.Base64.getEncoder().encodeToString(request.getReferenceImage().getBytes());
                partsList.add(Map.of("inline_data", Map.of(
                        "mime_type", request.getReferenceImage().getContentType() != null ? request.getReferenceImage().getContentType() : "image/jpeg",
                        "data", base64RefImage
                )));
            }

            Map<String, Object> content = Map.of("parts", partsList);
            Map<String, Object> body = Map.of("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            String resultJson = response.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();

            if (resultJson.startsWith("```json")) {
                resultJson = resultJson.substring(7, resultJson.length() - 3).trim();
            } else if (resultJson.startsWith("```")) {
                resultJson = resultJson.substring(3, resultJson.length() - 3).trim();
            }

            JsonNode resultNode = objectMapper.readTree(resultJson);
            int finalScore = resultNode.path("score").asInt();
            String aiComment = resultNode.path("comment").asText();

            // -------------------------------------------------------------
            // 1. UPLOAD ẢNH LÊN FIREBASE
            // -------------------------------------------------------------
            String originalName = request.getImage().getOriginalFilename();
            String safeName = originalName == null ? String.valueOf(System.currentTimeMillis()) : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String path = String.format("pose_battles/%d_%s", System.currentTimeMillis(), safeName);

            String uploadedImageUrl = firebaseStorageService.uploadFile(request.getImage(), path);

            // -------------------------------------------------------------
            // 2. LẤY USER ID TỪ JWT (SECURITY CONTEXT)
            // -------------------------------------------------------------
            Integer currentUserId = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Kiểm tra xem user đã đăng nhập chưa (có Authentication và Principal là String userId)
            if (authentication != null && authentication.getPrincipal() instanceof String && !authentication.getPrincipal().equals("anonymousUser")) {
                try {
                    currentUserId = Integer.parseInt((String) authentication.getPrincipal());
                } catch (NumberFormatException e) {
                    log.warn("Không thể parse userId từ JWT Principal: {}", authentication.getPrincipal());
                }
            }

            // -------------------------------------------------------------
            // 3. LƯU KẾT QUẢ VÀO DATABASE
            // -------------------------------------------------------------
            PoseScore newScoreRecord = PoseScore.builder()
                    .cosplayerId(currentUserId)
                    .imageUrl(uploadedImageUrl)
                    .score(BigDecimal.valueOf(finalScore))
                    .comment(aiComment)
                    .characterName(request.getCharacterName())
                    .build();

            // QUAN TRỌNG: Gán lại biến để lấy ID do Database tự động sinh ra
            newScoreRecord = poseScoreRepository.save(newScoreRecord);

            // Trả về DTO đầy đủ thông tin
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

    private List<Double> callGeminiGetVector(String text) {
        try {
            // Gọi đúng tên con AI mới nhất của Google: gemini-embedding-001
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=" + apiKey;

            // Xây dựng JSON Body đơn giản, không màu mè
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "models/gemini-embedding-001");

            ObjectNode content = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();
            ObjectNode textPart = objectMapper.createObjectNode();

            textPart.put("text", text);
            parts.add(textPart);
            content.set("parts", parts);

            body.set("content", content);

            // Gửi request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            // Bóc kết quả
            JsonNode valuesNode = response.path("embedding").path("values");
            List<Double> vector = new ArrayList<>();
            if (valuesNode != null && valuesNode.isArray()) {
                valuesNode.forEach(val -> vector.add(val.asDouble()));
            }
            return vector;

        } catch (Exception e) {
            log.error("Lỗi chi tiết khi gọi Gemini Vector: ", e);
            throw new RuntimeException("Gọi API Gemini Embedding thất bại: " + e.getMessage());
        }
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

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 14400000L)
    @Override
    public void generateVectorsForMissingImages() {
        List<Costume> allCostumes = costumeRepository.findCostumesMissingVector();
        int successCount = 0;

        log.info("Bắt đầu tạo Dual-Vector chạy ngầm cho {} bộ đồ...", allCostumes.size());

        for (Costume costume : allCostumes) {
            try {
                // TẠO IMAGE VECTOR (Từ 3 ảnh đầu)
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
                String imageVector = generateVectorForText(imageTags.toString());

                // TẠO TEXT VECTOR (Từ Tên + Mô tả)
                String textInput = ((costume.getName() != null ? costume.getName() : "") + " " + (costume.getDescription() != null ? costume.getDescription() : "")).trim();
                String textVector = generateVectorForText(textInput);

                if (imageVector != null) costume.setImageVector(imageVector);
                if (textVector != null) costume.setTextVector(textVector);

                costumeRepository.save(costume);
                successCount++;

                // Nghỉ 1 giây để Google khỏi chửi Spam API
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Lỗi tạo vector cho Costume {}: {}", costume.getId(), e.getMessage());
            }
        }
        log.info("Hoàn tất! Đã nâng cấp {} bộ đồ lên Dual-Vector.", successCount);
    }

    @Override
    public String generateCostumeDescription(String costumeName, String customPrompt, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return null;

        try {
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;

            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            // [NÂNG CẤP PROMPT] Linh hoạt ghép thêm tên bộ đồ nếu có
            String promptStr = "Bạn là một chuyên gia về trang phục cosplay. ";
            if (costumeName != null && !costumeName.trim().isEmpty()) {
                promptStr += "Tên nhân vật/bộ trang phục này là: '" + costumeName + "'. ";
            }
            promptStr += "Hãy nhìn vào những hình ảnh đính kèm và tạo ra một đoạn mô tả. ";
            // NẾU USER CÓ TRUYỀN YÊU CẦU RIÊNG THÌ ÉP AI LÀM THEO
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                promptStr += "ĐẶC BIỆT LƯU Ý YÊU CẦU SAU TỪ NGƯỜI DÙNG: " + customPrompt + ". ";
            }

            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("text", promptStr);
            partsNode.add(textPart);

            // Mã hóa ảnh sang Base64
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                String base64Image = java.util.Base64.getEncoder().encodeToString(file.getBytes());
                String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

                ObjectNode inlineData = objectMapper.createObjectNode();
                inlineData.put("mime_type", mimeType);
                inlineData.put("data", base64Image);

                ObjectNode imagePart = objectMapper.createObjectNode();
                imagePart.set("inline_data", inlineData);
                partsNode.add(imagePart);
            }

            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.set("parts", partsNode);
            ArrayNode contentsArray = objectMapper.createArrayNode();
            contentsArray.add(contentNode);
            body.set("contents", contentsArray);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey); // An toàn nhét Key vào Header
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            if (response != null && response.has("candidates")) {
                String resultText = response.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText().trim();
                return resultText.replaceAll("[*#_]", "");
            }
            return "Không thể generate được description, vui lòng tả thủ công.";
        } catch (Exception e) {
            log.error("Lỗi khi AI generate description: {}", e.getMessage());
            throw new RuntimeException("Hệ thống AI của Google đang quá tải (503). Bạn vui lòng đợi vài phút rồi thử bấm lại nha!");
        }
    }

    public String extractFeaturesFromMultipleImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return "";
        try {
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            // Prompt chung cho toàn bộ ảnh
            String promptText = "Trích xuất các từ khóa đặc trưng nhất của bộ trang phục xuất hiện trong TẤT CẢ các bức ảnh đính kèm. Tập trung vào: loại trang phục, màu sắc, họa tiết, và tên nhân vật. Chỉ trả về một chuỗi các từ khóa ngăn cách bằng dấu phẩy.";
            partsNode.add(objectMapper.createObjectNode().put("text", promptText));

            // Vòng lặp add tất cả ảnh vào chung 1 Request
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                String base64Image = java.util.Base64.getEncoder().encodeToString(file.getBytes());
                String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

                ObjectNode inlineData = objectMapper.createObjectNode();
                inlineData.put("mime_type", mimeType);
                inlineData.put("data", base64Image);
                partsNode.add(objectMapper.createObjectNode().set("inline_data", inlineData));
            }

            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            if (response != null && response.has("candidates")) {
                return response.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
            }
            return "";
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
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode partsNode = objectMapper.createArrayNode();

            // 1. Prompt để AI nhìn ảnh và bóc tag
            String promptText = "Hãy phân tích hình ảnh bộ trang phục này và liệt kê các từ khóa (tags) đặc trưng nhất. " +
                    "Tập trung vào: loại trang phục, màu sắc, họa tiết, chất liệu, phong cách, và tên nhân vật nếu nhận diện được. " +
                    "Chỉ trả về chuỗi các từ khóa ngăn cách bằng dấu phẩy, không giải thích dài dòng.";
            partsNode.add(objectMapper.createObjectNode().put("text", promptText));

            // 2. Chuyển byte[] sang Base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mime_type", "image/jpeg"); // Mặc định là jpeg vì tải từ Firebase
            inlineData.put("data", base64Image);

            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.set("inline_data", inlineData);
            partsNode.add(imagePart);

            // 3. Xây dựng payload và gửi request
            body.set("contents", objectMapper.createArrayNode().add(objectMapper.createObjectNode().set("parts", partsNode)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            if (response != null && response.has("candidates")) {
                return response.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText().trim();
            }
            return "";
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
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;

            // 1. CHUẨN BỊ DATA ĐẦU VÀO DƯỚI DẠNG MẢNG JSON
            ArrayNode inputJsonArray = objectMapper.createArrayNode();
            for (int i = 0; i < requests.size(); i++) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("id", i); // GẮN ID ĐỂ ÉP AI KHÔNG ĐƯỢC BỎ SÓT
                item.put("question", requests.get(i).getQuestionContext());
                item.put("answer", requests.get(i).getUserAnswer());
                inputJsonArray.add(item);
            }

            // 2. PROMPT THẦN CHÚ CHỐNG LƯỜI (ANTI-LAZY PROMPT)
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

            Map<String, Object> textPart = Map.of("text", promptText);
            Map<String, Object> content = Map.of("parts", List.of(textPart));
            Map<String, Object> body = Map.of("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            if (response != null && response.has("candidates")) {
                String resultJson = response.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText().trim();

                // Gọt bỏ markdown rác
                if (resultJson.startsWith("```json")) {
                    resultJson = resultJson.substring(7, resultJson.length() - 3).trim();
                } else if (resultJson.startsWith("```")) {
                    resultJson = resultJson.substring(3, resultJson.length() - 3).trim();
                }

                // 3. MAP KẾT QUẢ VÀO DANH SÁCH TRẢ VỀ THEO ĐÚNG THỨ TỰ
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

                // Xử lý Fallback nếu AI vô tình bỏ sót 1 vài câu (rất hiếm khi xảy ra với prompt trên)
                for (int i = 0; i < finalResponses.size(); i++) {
                    if (finalResponses.get(i) == null) {
                        finalResponses.set(i, CustomAnswerResponse.builder()
                                .isValid(false)
                                .reason("AI từ chối phân tích hoặc xảy ra lỗi bỏ sót.")
                                .build());
                    }
                }

                return finalResponses;
            }
            throw new RuntimeException("AI không trả về kết quả hợp lệ.");

        } catch (Exception e) {
            log.error("Lỗi khi phân tích câu trả lời Custom Batch: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi phân tích AI: " + e.getMessage());
        }
    }
}