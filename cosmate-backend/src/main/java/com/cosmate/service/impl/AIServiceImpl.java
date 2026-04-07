package com.cosmate.service.impl;

import com.cosmate.configuration.AiKnowledgeBase;
import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeImage;
import com.cosmate.entity.PoseScore;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.PoseScoreRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
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

    @Value("${gemini.api.key}")
    private String apiKey;

    // Các hằng số cho Model AI của Google
    private static final String EMBEDDING_MODEL_URL = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent";
    private static final String GENERATION_MODEL_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    /**
     * Tìm kiếm các trang phục có độ tương đồng cao bằng thuật toán Cosine Similarity.
     */
    @Override
    public List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request) {
        try {
            String queryText = request.getText() != null ? request.getText().trim() : "";
            List<MultipartFile> imageFiles = request.getFiles();

            if (imageFiles == null || imageFiles.isEmpty() || imageFiles.get(0).isEmpty()) {
                throw new IllegalArgumentException("Tính năng tìm kiếm bằng AI bắt buộc phải upload hình ảnh!");
            }

            StringBuilder searchPrompt = new StringBuilder();

            // 1. Trích xuất đặc điểm từ NHIỀU ảnh
            if (imageFiles != null && !imageFiles.isEmpty()) {
                String imageFeatures = extractFeaturesFromMultipleImages(imageFiles); // Gọi đúng tên hàm mới
                searchPrompt.append(imageFeatures).append(". ");
                log.info("AI trích xuất từ khóa từ ảnh: {}", imageFeatures);
            }

            // 2. Ghép thêm text user nhập (nếu có)
            if (!queryText.isEmpty()) {
                searchPrompt.append("Yêu cầu thêm: ").append(queryText);
            }

            String finalSearchContent = searchPrompt.toString().trim();
            log.info("Nội dung tổng hợp đem đi tạo Vector: {}", finalSearchContent);

            // 3. Tạo vector từ nội dung tổng hợp
            List<Double> queryVector = callGeminiGetVector(finalSearchContent);

            // 4. Tính toán điểm tương đồng (Cosine Similarity)
            List<Costume> allCostumes = costumeRepository.findAll(); // Hoặc ông tạo hàm findAllWithVector() bên repository
            List<SearchResponse> results = new ArrayList<>();
            final double SIMILARITY_THRESHOLD = 0.65;

            for (Costume costume : allCostumes) {
                // Bỏ qua nếu bộ đồ chưa được tạo vector
                if (costume.getCostumeVector() == null || costume.getCostumeVector().isEmpty()) continue;

                List<Double> dbVector = objectMapper.readValue(costume.getCostumeVector(), new TypeReference<>() {});
                double score = calculateCosineSimilarity(queryVector, dbVector);

                if (score > SIMILARITY_THRESHOLD) {
                    // Lấy ảnh đầu tiên của bộ đồ để hiển thị (nếu có)
                    String displayImageUrl = costume.getImages().isEmpty() ? "" : costume.getImages().get(0).getImageUrl();

                    results.add(SearchResponse.builder()
                            .costumeId(costume.getId())
                            .costumeName(costume.getName())
                            .imageUrl(displayImageUrl)
                            .price(costume.getPricePerDay())
                            .similarityScore(score)
                            .build());
                }
            }

            // 5. Sắp xếp giảm dần và lấy top 10
            return results.stream()
                    .sorted(Comparator.comparingDouble(SearchResponse::getSimilarityScore).reversed())
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi trong quá trình AI tìm kiếm: {}", e.getMessage(), e);
            throw new RuntimeException("Tìm kiếm AI thất bại: " + e.getMessage());
        }
    }

    /**
     * Tạo vector nhúng (embedding) cho trang phục và lưu vào Database.
     */
    @Override
    public void generateAndSaveVector(Integer costumeId) {
        costumeRepository.findById(costumeId).ifPresent(costume -> {
            try {
                StringBuilder combinedText = new StringBuilder();
                combinedText.append(costume.getName()).append(" ").append(costume.getDescription());

                // 1. Phải lấy thêm Tag ẩn từ đống ảnh hiện có trong DB
                if (!costume.getImages().isEmpty()) {
                    // Chỉ cần lấy ảnh đầu tiên (ảnh MAIN) để AI nhìn là đủ đại diện
                    String imageUrl = costume.getImages().get(0).getImageUrl();
                    byte[] imageBytes = downloadImageFromUrl(imageUrl);
                    if (imageBytes != null) {
                        String hiddenTags = extractTagsFromBytes(imageBytes);
                        combinedText.append(" ").append(hiddenTags);
                    }
                }

                // 2. Tạo vector từ chuỗi text đã được "cường hóa"
                String vectorStr = generateVectorForText(combinedText.toString());
                costume.setCostumeVector(vectorStr);
                costumeRepository.save(costume);

                log.info("Đã cập nhật Siêu Vector thủ công cho Costume ID: {}", costumeId);
            } catch (Exception e) {
                log.error("Tạo vector thủ công thất bại cho ID {}: {}", costumeId, e.getMessage());
            }
        });
    }

    /**
     * Phân tích hồ sơ người dùng và đưa ra gợi ý nhân vật Cosplay phù hợp.
     */
    @Override
    public List<SearchResponse> recommendCosplay(RecommendationRequest request) {
        try {
            // 1. Trích xuất dữ liệu học thuật từ RAG (In-Memory Cache)
            JsonNode archetypes = aiKnowledgeBase.getArchetypes();
            JsonNode targetArchetype = null;

            for (JsonNode node : archetypes) {
                if (node.path("archetype_id").asText().equals(request.getArchetypeId())) {
                    targetArchetype = node;
                    break;
                }
            }

            if (targetArchetype == null) {
                throw new RuntimeException("Không tìm thấy dữ liệu Archetype trong RAG!");
            }

            String archName = targetArchetype.path("archetype_name").asText();
            String colors = targetArchetype.path("color_palette").toString();
            String style = targetArchetype.path("clothing_style").asText();

            // Tìm thông tin Subtype chi tiết
            String subTypeName = "";
            JsonNode subTypes = targetArchetype.path("sub_types");
            for (JsonNode sub : subTypes) {
                if (sub.path("id").asText().equals(request.getSubTypeId())) {
                    subTypeName = sub.path("name").asText();
                    break;
                }
            }

            // 2. Tạo nội dung Vector Search từ dữ liệu Tâm lý học
            String searchContent = String.format("Trang phục dành cho nguyên mẫu %s, cụ thể là %s. Phong cách quần áo chủ đạo: %s. Bảng màu ưu tiên: %s. Mức ngân sách: %s.",
                    archName, subTypeName, style, colors, request.getBudgetMetadata());

            log.info("Nội dung đưa vào Vector AI Suggestion: {}", searchContent);

            // 3. Tạo Vector và tự đi so khớp (Không gọi ké hàm search ảnh nữa)
            List<Double> queryVector = callGeminiGetVector(searchContent);
            List<Costume> allCostumes = costumeRepository.findAll();
            List<SearchResponse> results = new ArrayList<>();

            // Recommend thì lấy ngưỡng thấp một chút (0.50) để kết quả đa dạng, phong phú hơn
            final double RECOMMEND_THRESHOLD = 0.50;

            for (Costume costume : allCostumes) {
                if (costume.getCostumeVector() == null || costume.getCostumeVector().isEmpty()) continue;

                List<Double> dbVector = objectMapper.readValue(costume.getCostumeVector(), new TypeReference<>() {});
                double score = calculateCosineSimilarity(queryVector, dbVector);

                if (score > RECOMMEND_THRESHOLD) {
                    String displayImageUrl = costume.getImages().isEmpty() ? "" : costume.getImages().get(0).getImageUrl();
                    results.add(SearchResponse.builder()
                            .costumeId(costume.getId())
                            .costumeName(costume.getName())
                            .imageUrl(displayImageUrl)
                            .price(costume.getPricePerDay())
                            .similarityScore(score)
                            .build());
                }
            }

            // 4. Lấy Top 30 kết quả đỉnh nhất trả về cho người dùng
            return results.stream()
                    .sorted(Comparator.comparingDouble(SearchResponse::getSimilarityScore).reversed())
                    .limit(30)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi hệ thống Recommend RAG: {}", e.getMessage(), e);
            throw new RuntimeException("Gợi ý thất bại: " + e.getMessage());
        }
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
            String base64Image = Base64.getEncoder().encodeToString(request.getImage().getBytes());

            String promptText = "Bạn là một giám khảo Cosplay. Dưới đây là bộ tiêu chuẩn WCS chính thức:\n"
                    + aiKnowledgeBase.getWcsRules() + "\n"
                    + "Hãy so sánh ảnh user chụp với nhân vật '" + request.getCharacterName() + "' và chấm điểm nghiêm ngặt theo luật trên. "
                    + "Chỉ trả về JSON định dạng: "
                    + "{\"score\": [Điểm tổng 1-100], \"pose_score\": [1-40], \"expression_score\": [1-40], \"costume_score\": [1-20], \"comment\": \"[Nhận xét kỹ thuật...]\"}";

            Map<String, Object> textPart = Map.of("text", promptText);
            Map<String, Object> imagePart = Map.of("inline_data", Map.of(
                    "mime_type", request.getImage().getContentType() != null ? request.getImage().getContentType() : "image/jpeg",
                    "data", base64Image
            ));

            Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
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
                    .build();

            poseScoreRepository.save(newScoreRecord);

            return PoseScoringResponse.builder()
                    .score(finalScore)
                    .comment(aiComment)
                    .build();

        } catch (Exception e) {
            log.error("AI chấm điểm Pose thất bại: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi chấm điểm Pose: " + e.getMessage());
        }
    }

    // --- Các hàm tiện ích (Utility Methods) ---

    private List<Double> callGeminiGetVector(String text) {
        try {
            // [SỰ THẬT CHÂN LÝ] Gọi đúng tên con AI mới nhất của Google: gemini-embedding-001
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

    private String callGeminiGenerateText(String prompt) {
        try {
            String url = GENERATION_MODEL_URL + "?key=" + apiKey;
            ObjectNode contentPart = objectMapper.createObjectNode().put("text", prompt);
            ArrayNode parts = objectMapper.createArrayNode().add(contentPart);
            ObjectNode content = objectMapper.createObjectNode().set("parts", parts);
            ObjectNode body = objectMapper.createObjectNode().set("contents", objectMapper.createArrayNode().add(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            if (response != null && response.has("candidates")) {
                return response.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText();
            }
            return "Trang phục cosplay chung";
        } catch (Exception e) {
            log.error("Gọi API Gemini Generation thất bại: {}", e.getMessage(), e);
            return "Trang phục cosplay nổi bật"; // Trả về kết quả mặc định an toàn nếu có lỗi
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

    @Override
    public void generateVectorsForMissingImages() {
        List<Costume> allCostumes = costumeRepository.findAll();
        int successCount = 0;

        for (Costume costume : allCostumes) {
            if (costume.getCostumeVector() == null || costume.getCostumeVector().trim().isEmpty()) {
                try {
                    StringBuilder combinedText = new StringBuilder();
                    combinedText.append(costume.getName()).append(" ").append(costume.getDescription());

                    // 1. Lấy ảnh đầu tiên của bộ đồ để AI "nhìn"
                    if (!costume.getImages().isEmpty()) {
                        String firstImageUrl = costume.getImages().get(0).getImageUrl();
                        byte[] imageBytes = downloadImageFromUrl(firstImageUrl);

                        if (imageBytes != null) {
                            // 2. Chế biến byte[] thành định dạng Gemini hiểu được để lấy Tag
                            String hiddenTags = extractTagsFromBytes(imageBytes);
                            combinedText.append(" ").append(hiddenTags);
                            log.info("Costume ID {}: Đã bóc được tag ẩn: {}", costume.getId(), hiddenTags);
                        }
                    }

                    // 3. Tạo "Siêu Vector" từ (Tên + Mô tả + Tag ẩn)
                    String vectorStr = generateVectorForText(combinedText.toString());

                    if (vectorStr != null) {
                        costume.setCostumeVector(vectorStr);
                        costumeRepository.save(costume);
                        successCount++;
                    }

                    Thread.sleep(500); // Tránh bị Google chặn do gọi quá nhanh

                } catch (Exception e) {
                    log.error("Lỗi tạo vector cho Costume {}: {}", costume.getId(), e.getMessage());
                }
            }
        }
        log.info("Hoàn tất! Đã nâng cấp {} bộ đồ lên Siêu Vector.", successCount);
    }

    @Override
    public String generateCostumeDescription(String costumeName, List<MultipartFile> files) {
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
            promptStr += "Hãy nhìn vào những hình ảnh đính kèm và tạo ra một đoạn mô tả chi tiết, hấp dẫn và chính xác cho bộ đồ này để đăng bán trên sàn thương mại điện tử CosMate. Tập trung vào kiểu dáng, màu sắc, hoa văn, vật liệu nhìn thấy được. Hãy làm cho mô tả trở nên thu hút người thuê/mua. Chỉ trả về văn bản mô tả, không kèm theo lời dẫn hay định dạng markdown (như dấu sao, dấu thăng).";

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
            return "Lỗi khi generate description: " + e.getMessage();
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
    public List<PoseScore> getPoseHistoryByUserId(Integer userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return poseScoreRepository.findByCosplayerIdOrderByCreatedAtDesc(userId);
    }
}