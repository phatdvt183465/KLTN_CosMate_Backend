package com.cosmate.service.impl;

import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.entity.CostumeImage;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.service.AISearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AISearchServiceImpl implements AISearchService {

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
}