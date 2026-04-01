package com.cosmate.service.impl;

import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.entity.CostumeImage;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.service.AIService;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    private final CostumeImageRepository costumeImageRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

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
            String queryText = request.getText();
            if (queryText == null || queryText.trim().isEmpty()) {
                throw new IllegalArgumentException("Từ khóa tìm kiếm không được để trống.");
            }

            // 1. Tạo vector đại diện cho từ khóa tìm kiếm
            List<Double> queryVector = callGeminiGetVector(queryText);

            // 2. Lấy tất cả hình ảnh đã được mã hóa vector từ Database
            List<CostumeImage> allImages = costumeImageRepository.findAllWithVector();

            // 3. Tính toán điểm tương đồng và lọc các kết quả trùng lặp
            Map<Integer, SearchResponse> uniqueResults = new HashMap<>();
            final double SIMILARITY_THRESHOLD = 0.55;

            for (CostumeImage img : allImages) {
                List<Double> dbVector = objectMapper.readValue(img.getImageVector(), new TypeReference<>() {});
                double score = calculateCosineSimilarity(queryVector, dbVector);

                if (score > SIMILARITY_THRESHOLD) {
                    Integer costumeId = img.getCostume().getId();

                    // Chỉ giữ lại hình ảnh có điểm tương đồng cao nhất cho mỗi bộ trang phục
                    if (!uniqueResults.containsKey(costumeId) || score > uniqueResults.get(costumeId).getSimilarityScore()) {
                        uniqueResults.put(costumeId, SearchResponse.builder()
                                .costumeId(costumeId)
                                .costumeName(img.getCostume().getName())
                                .imageUrl(img.getImageUrl())
                                .price(img.getCostume().getPricePerDay())
                                .similarityScore(score)
                                .build());
                    }
                }
            }

            // 4. Sắp xếp điểm giảm dần và trả về top 10 kết quả khớp nhất
            return uniqueResults.values().stream()
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
    public void generateAndSaveVector(Integer costumeImageId) {
        costumeImageRepository.findById(costumeImageId).ifPresent(img -> {
            try {
                String contentToEmbed = img.getCostume().getName() + " " + img.getCostume().getDescription();
                List<Double> vector = callGeminiGetVector(contentToEmbed);

                img.setImageVector(objectMapper.writeValueAsString(vector));
                costumeImageRepository.save(img);
                log.info("Đã tạo và lưu vector thành công cho CostumeImage ID: {}", costumeImageId);
            } catch (Exception e) {
                log.error("Tạo vector thất bại cho CostumeImage ID {}: {}", costumeImageId, e.getMessage());
            }
        });
    }

    /**
     * Phân tích hồ sơ người dùng và đưa ra gợi ý nhân vật Cosplay phù hợp.
     */
    public List<SearchResponse> recommendCosplay(RecommendationRequest request) {
        String userProfile = String.format(
                "Giới tính: %s. Phong cách: %s. Màu yêu thích: %s. Ngân sách: %s. Sở thích: %s.",
                request.getGender(), request.getStyle(), request.getFavoriteColor(), request.getBudgetRange(), request.getHobby()
        );

        String prompt = "Dựa trên hồ sơ người dùng sau: [" + userProfile + "]. " +
                "Hãy gợi ý duy nhất 01 nhân vật Anime/Game/Manga nổi tiếng phù hợp nhất để họ cosplay. " +
                "Chỉ trả về 01 câu mô tả ngắn gọn về đặc điểm trang phục của nhân vật đó bằng tiếng Việt để dùng làm từ khóa tìm kiếm. " +
                "Ví dụ: 'Trang phục Kimono màu hồng ống tre xanh'. Không giải thích thêm.";

        String suggestedKeyword = callGeminiGenerateText(prompt);
        log.info("Từ khóa AI gợi ý: {}", suggestedKeyword);

        SearchByImageRequest searchRequest = new SearchByImageRequest();
        searchRequest.setText(suggestedKeyword);

        return searchSimilarCostumes(searchRequest);
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

            String promptText = "Bạn là một giám khảo chấm điểm Cosplay chuyên nghiệp. " +
                    "So sánh mức độ hóa trang, tạo dáng, biểu cảm của người trong ảnh với nhân vật gốc: '" + request.getCharacterName() + "'. " +
                    "Chấm điểm từ 1 đến 100. " +
                    "Chỉ trả về ĐÚNG một chuỗi JSON hợp lệ, KHÔNG có markdown: " +
                    "{\"score\": 85, \"comment\": \"Lời nhận xét ngắn gọn...\"}";

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

            // Làm sạch chuỗi JSON để loại bỏ định dạng Markdown nếu AI sinh ra
            if (resultJson.startsWith("```json")) {
                resultJson = resultJson.substring(7, resultJson.length() - 3).trim();
            } else if (resultJson.startsWith("```")) {
                resultJson = resultJson.substring(3, resultJson.length() - 3).trim();
            }

            JsonNode resultNode = objectMapper.readTree(resultJson);
            return PoseScoringResponse.builder()
                    .score(resultNode.path("score").asInt())
                    .comment(resultNode.path("comment").asText())
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
        // 1. Lấy toàn bộ ảnh trong DB
        List<CostumeImage> allImages = costumeImageRepository.findAll();
        List<CostumeImage> imagesToUpdate = new ArrayList<>();

        int successCount = 0;

        // 2. Lọc ra những ảnh đang bị null hoặc rỗng ở cột image_vector
        for (CostumeImage img : allImages) {
            if (img.getImageVector() == null || img.getImageVector().trim().isEmpty()) {
                if (img.getCostume() != null) {
                    try {
                        // Lấy Tên + Mô tả để tạo Vector
                        String textForVector = img.getCostume().getName() + " " + img.getCostume().getDescription();
                        String vectorStr = generateVectorForText(textForVector);

                        if (vectorStr != null && !vectorStr.isEmpty()) {
                            img.setImageVector(vectorStr);
                            imagesToUpdate.add(img);
                            successCount++;
                            log.info("Đã tạo thành công vector cho ảnh ID: {}", img.getId());
                        }
                    } catch (Exception e) {
                        log.error("Lỗi khi tạo vector cho ảnh ID {}: {}", img.getId(), e.getMessage());
                        // Bỏ qua lỗi để vòng lặp chạy tiếp các ảnh khác
                    }
                }
            }
        }

        // 3. Lưu hàng loạt xuống DB
        if (!imagesToUpdate.isEmpty()) {
            costumeImageRepository.saveAll(imagesToUpdate);
            log.info("Đã quét và cập nhật thành công tổng cộng {} vectors!", successCount);
        } else {
            log.info("Không có ảnh nào cần cập nhật vector.");
        }
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
}