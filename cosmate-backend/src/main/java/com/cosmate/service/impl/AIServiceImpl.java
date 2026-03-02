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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    private final CostumeImageRepository costumeImageRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/embedding-001:embedContent";

    @Override
    public List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request) {
        try {
            // 1. Lấy vector từ Text query
            String queryText = request.getText();
            if (queryText == null || queryText.isEmpty()) throw new RuntimeException("Vui lòng nhập mô tả tìm kiếm");
            List<Double> queryVector = callGeminiGetVector(queryText);

            // 2. Lấy tất cả ảnh có vector
            List<CostumeImage> allImages = costumeImageRepository.findAllWithVector();

            // 3. Tính điểm và LỌC TRÙNG (Dùng Map để chỉ giữ lại bộ đồ có điểm cao nhất)
            Map<Integer, SearchResponse> uniqueResults = new HashMap<>();

            for (CostumeImage img : allImages) {
                // Parse vector từ DB
                List<Double> dbVector = objectMapper.readValue(img.getImageVector(), new TypeReference<>() {});

                // Tính điểm giống nhau
                double score = cosineSimilarity(queryVector, dbVector);

                if (score > 0.55) { // Hạ threshold xuống xíu cho dễ ra kết quả test
                    Integer costumeId = img.getCostume().getId();

                    // Logic lọc trùng:
                    // Nếu bộ đồ này chưa có trong map HOẶC bộ đồ này đã có nhưng ảnh mới này điểm cao hơn
                    // -> Thì cập nhật vào map
                    if (!uniqueResults.containsKey(costumeId) || score > uniqueResults.get(costumeId).getSimilarityScore()) {

                        uniqueResults.put(costumeId, SearchResponse.builder()
                                .costumeId(costumeId)
                                .costumeName(img.getCostume().getName()) // Khớp với entity Costume
                                .imageUrl(img.getImageUrl()) // Lấy ảnh khớp nhất để hiển thị
                                .price(img.getCostume().getPricePerDay()) // Khớp với entity Costume
                                .similarityScore(score)
                                .build());
                    }
                }
            }

            // 4. Chuyển Map thành List, Sắp xếp và Lấy Top 10
            return uniqueResults.values().stream()
                    .sorted(Comparator.comparingDouble(SearchResponse::getSimilarityScore).reversed())
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi AI Search: " + e.getMessage());
        }
    }

    @Override
    public void generateAndSaveVector(Integer costumeImageId) {
        // Hàm này dùng để gọi khi Provider vừa up ảnh xong
        CostumeImage img = costumeImageRepository.findById(costumeImageId).orElse(null);
        if (img == null) return;

        // Tạo vector từ Tên trang phục + Mô tả (Vì vector hóa text chính xác hơn ảnh raw với model free)
        String contentToEmbed = img.getCostume().getName() + " " + img.getCostume().getDescription();
        try {
            List<Double> vector = callGeminiGetVector(contentToEmbed);
            img.setImageVector(objectMapper.writeValueAsString(vector)); // Lưu mảng số thành chuỗi JSON
            costumeImageRepository.save(img);
        } catch (Exception e) {
            System.err.println("Lỗi tạo vector cho ảnh ID " + costumeImageId);
        }
    }

    // --- Private Helpers ---

    private List<Double> callGeminiGetVector(String text) {
        try {
            String url = GEMINI_URL + "?key=" + apiKey;

            // Body JSON chuẩn của Gemini
            Map<String, Object> contentPart = Map.of("text", text);
            Map<String, Object> content = Map.of("parts", List.of(contentPart));
            Map<String, Object> body = Map.of("model", "models/embedding-001", "content", content);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // Gọi API
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            // Parse kết quả
            JsonNode valuesNode = response.path("embedding").path("values");
            List<Double> vector = new ArrayList<>();
            if (valuesNode.isArray()) {
                for (JsonNode val : valuesNode) {
                    vector.add(val.asDouble());
                }
            }
            return vector;
        } catch (Exception e) {
            throw new RuntimeException("Không gọi được Gemini API: " + e.getMessage());
        }
    }

    // Thuật toán Cosine Similarity (Toán học cấp 3 :D)
    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.size() != v2.size()) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Gợi ý nhân vật dựa trên sở thích
    public List<SearchResponse> recommendCosplay(RecommendationRequest request) {
        // 1. Tạo Prompt dựa trên câu trả lời của User
        String userProfile = String.format(
                "Giới tính: %s. Phong cách: %s. Màu yêu thích: %s. Ngân sách: %s. Sở thích: %s.",
                request.getGender(), request.getStyle(), request.getFavoriteColor(), request.getBudgetRange(), request.getHobby()
        );

        String prompt = "Dựa trên hồ sơ người dùng sau: [" + userProfile + "]. " +
                "Hãy gợi ý duy nhất 01 nhân vật Anime/Game/Manga nổi tiếng phù hợp nhất để họ cosplay. " +
                "Chỉ trả về 01 câu mô tả ngắn gọn về đặc điểm trang phục của nhân vật đó bằng tiếng Việt để tôi dùng làm từ khóa tìm kiếm. " +
                "Ví dụ output: 'Trang phục Kimono màu hồng ống tre xanh'. Không giải thích gì thêm.";

        // 2. Gọi Gemini (Dùng hàm generate content như cái tạo prompt ảnh)
        String suggestedKeyword = callGeminiGenerateText(prompt); // Hàm này em viết ở dưới

        // 3. Có từ khóa rồi -> Gọi lại hàm Search cũ để tìm đồ trong DB
        SearchByImageRequest searchRequest = new SearchByImageRequest();
        searchRequest.setText(suggestedKeyword);

        System.out.println("AI Gợi ý tìm kiếm: " + suggestedKeyword); // Log ra để debug xem nó gợi ý gì

        return searchSimilarCostumes(searchRequest); // TÁI SỬ DỤNG code cũ
    }

    private String callGeminiGenerateText(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

            // Body JSON
            ObjectNode contentPart = objectMapper.createObjectNode().put("text", prompt);
            ArrayNode parts = objectMapper.createArrayNode().add(contentPart);
            ObjectNode content = objectMapper.createObjectNode().set("parts", parts);
            ObjectNode body = objectMapper.createObjectNode().set("contents", objectMapper.createArrayNode().add(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);

            return response.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            return "Trang phục anime nổi tiếng"; // Fallback nếu lỗi
        }
    }

    // Check 18+
    @Override
    public void validateImageContent(MultipartFile file) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent?key=" + apiKey;

            // 1. Encode ảnh sang Base64
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            // 2. Tạo Prompt Check
            String promptText = "Bạn là hệ thống kiểm duyệt nội dung (Content Moderator). " +
                    "Hãy phân tích hình ảnh này. Nếu nó chứa nội dung người lớn (nudity, sexual), bạo lực máu me, hoặc phản cảm, hãy trả lời 'UNSAFE'. " +
                    "Nếu hình ảnh an toàn, phù hợp cho mọi lứa tuổi (bao gồm cả trang phục cosplay gợi cảm nhưng không hở hang quá mức), hãy trả lời 'SAFE'. " +
                    "Chỉ trả về đúng 1 từ: SAFE hoặc UNSAFE.";

            // 3. Build Body JSON cho Gemini Vision
            // Cấu trúc: { contents: [{ parts: [{ text: ... }, { inline_data: { mime_type: ..., data: ... } }] }] }
            Map<String, Object> textPart = Map.of("text", promptText);
            Map<String, Object> imagePart = Map.of("inline_data", Map.of(
                    "mime_type", file.getContentType() != null ? file.getContentType() : "image/jpeg",
                    "data", base64Image
            ));

            Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
            Map<String, Object> body = Map.of("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // 4. Gọi API
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            String result = response.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();

            // 5. Xử lý kết quả
            if ("UNSAFE".equalsIgnoreCase(result)) {
                throw new RuntimeException("Ảnh vi phạm tiêu chuẩn cộng đồng (18+ hoặc bạo lực).");
            }

        } catch (Exception e) {
            // Bắt tất cả lỗi (bao gồm IOException) và ném ra RuntimeException
            // RuntimeException không bắt buộc phải khai báo "throws" nên Interface sẽ chịu nhận
            if (e.getMessage().contains("Ảnh vi phạm")) {
                throw new RuntimeException(e.getMessage());
            }
            throw new RuntimeException("Lỗi xử lý ảnh AI: " + e.getMessage());
        }
    }

    // --- HÀM MỚI: AI Chấm điểm Pose ---
    @Override
    public PoseScoringResponse scorePose(PoseScoringRequest request) {
        try {
            // Vẫn dùng model vision như lúc check ảnh 18+
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent?key=" + apiKey;

            // Encode ảnh gửi lên
            String base64Image = Base64.getEncoder().encodeToString(request.getImage().getBytes());

            // Prompt mớm cho giám khảo AI
            String promptText = "Bạn là một giám khảo chấm điểm Cosplay chuyên nghiệp. " +
                    "Hãy xem bức ảnh này và so sánh mức độ hóa trang, tạo dáng, biểu cảm của người trong ảnh với nhân vật gốc là: '" + request.getCharacterName() + "'. " +
                    "Chấm điểm từ 1 đến 100. " +
                    "Chỉ trả về ĐÚNG một chuỗi JSON hợp lệ với định dạng như sau, KHÔNG có markdown, KHÔNG có văn bản thừa: " +
                    "{\"score\": 85, \"comment\": \"Lời nhận xét ngắn gọn, vui nhộn của bạn ở đây...\"}";

            // Build Body JSON
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

            // Gọi API
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            String resultJson = response.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();

            // Dọn dẹp nếu AI trả về cục markdown (```json ... ```)
            if (resultJson.startsWith("```json")) {
                resultJson = resultJson.substring(7, resultJson.length() - 3).trim();
            } else if (resultJson.startsWith("```")) {
                resultJson = resultJson.substring(3, resultJson.length() - 3).trim();
            }

            // Convert String JSON thành Object trả về
            JsonNode resultNode = objectMapper.readTree(resultJson);
            return PoseScoringResponse.builder()
                    .score(resultNode.path("score").asInt())
                    .comment(resultNode.path("comment").asText())
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi khi giám khảo AI chấm điểm: " + e.getMessage());
        }
    }
}