package com.cosmate.controller;

import com.cosmate.configuration.AiKnowledgeBase;
import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final AiKnowledgeBase aiKnowledgeBase;

    // API tìm kiếm: POST /api/search/ai
    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<SearchResponse>> searchByAI(@ModelAttribute SearchByImageRequest request) {
        try {
            // Cố gắng gọi AI trước
            List<SearchResponse> results = aiService.searchSimilarCostumes(request);
            return ApiResponse.<List<SearchResponse>>builder()
                    .result(results)
                    .message("Kết quả tìm kiếm AI thông minh!")
                    .build();

        } catch (Exception e) {
            // Nếu AI ném lỗi (hết Quota, sai Key, rớt mạng...) -> Bắt lỗi ngay
            String text = request.getText() != null ? request.getText() : "";
            List<SearchResponse> fallbackResults = aiService.fallbackSearch(text);

            return ApiResponse.<List<SearchResponse>>builder()
                    .result(fallbackResults)
                    // Báo thẳng cho frontend biết để hiển thị Toast/Alert
                    .message("Hệ thống AI đang bảo trì hoặc quá tải. Trả về kết quả tìm kiếm thông thường cho từ khóa: " + text)
                    .build();
        }
    }

    // Đổi URL và param từ costumeImageId sang costumeId
    @PostMapping("/generate-vector/{costumeId}")
    public ApiResponse<Void> generateVector(@PathVariable Integer costumeId) {
        aiService.generateAndSaveVector(costumeId);
        return ApiResponse.<Void>builder().message("Đã tạo vector thành công!").build();
    }

    @PostMapping("/recommend")
    public ApiResponse<List<SearchResponse>> recommend(@RequestBody RecommendationRequest request) {
        return ApiResponse.<List<SearchResponse>>builder()
                .result(aiService.recommendCosplay(request))
                .message("Đây là các bộ đồ phù hợp với cá tính của bạn!")
                .build();
    }

    // API Chấm điểm Pose: POST /api/search/pose-score
    @PostMapping(value = "/pose-score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PoseScoringResponse> scorePose(@ModelAttribute PoseScoringRequest request) {
        return ApiResponse.<PoseScoringResponse>builder()
                .result(aiService.scorePose(request))
                .message("Chấm điểm thành công!")
                .build();
    }

    // API tool: Quét và tạo vector cho các ảnh cũ bị thiếu
    @PostMapping("/generate-missing-vectors")
    public ApiResponse<Void> generateMissingVectors() {
        aiService.generateVectorsForMissingImages();
        return ApiResponse.<Void>builder()
                .message("Đang chạy ngầm quá trình quét và cập nhật Vector. Vui lòng check Console log!")
                .build();
    }

    // Tiện ích: AI tự động viết mô tả chi tiết cho trang phục dựa trên ảnh
    @PostMapping(value = "/generate-description", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> generateDescription(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam("files") List<MultipartFile> files) {

        String description = aiService.generateCostumeDescription(name, files);
        return ApiResponse.<String>builder()
                .message("Tạo mô tả thành công!")
                .result(description)
                .build();
    }

    // Frontend gọi API này để lấy 6 câu hỏi đầu tiên
    @GetMapping("/stage-1")
    public ApiResponse<JsonNode> getStage1Survey() {
        return ApiResponse.<JsonNode>builder()
                .result(aiKnowledgeBase.getStage1Survey())
                .message("Tải bộ câu hỏi Quick Test thành công!")
                .build();
    }

    // Khi có kết quả Archetype ID (VD: ARCH_01), Frontend gọi API này để lấy bộ câu hỏi chuyên sâu
    @GetMapping("/stage-2/{archetypeId}")
    public ApiResponse<JsonNode> getStage2Survey(@PathVariable String archetypeId) {
        JsonNode allStage2 = aiKnowledgeBase.getStage2Survey();
        JsonNode specificQuestions = allStage2.path(archetypeId);

        if (specificQuestions.isMissingNode()) {
            throw new RuntimeException("Không tìm thấy bộ câu hỏi cho Archetype này!");
        }

        return ApiResponse.<JsonNode>builder()
                .result(specificQuestions)
                .message("Tải bộ câu hỏi Deep Analysis thành công!")
                .build();
    }

    // (Tùy chọn) Khuyến mãi thêm cái API lấy danh sách Archetype nếu sau này Frontend cần
    @GetMapping("/archetypes")
    public ApiResponse<JsonNode> getAllArchetypes() {
        return ApiResponse.<JsonNode>builder()
                .result(aiKnowledgeBase.getArchetypes())
                .build();
    }
}