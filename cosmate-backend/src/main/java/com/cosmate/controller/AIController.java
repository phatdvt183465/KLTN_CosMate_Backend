package com.cosmate.controller;

import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;

    // API tìm kiếm: POST /api/search/ai
    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<SearchResponse>> searchByAI(@ModelAttribute SearchByImageRequest request) {
        return ApiResponse.<List<SearchResponse>>builder()
                .result(aiService.searchSimilarCostumes(request))
                .message("Kết quả tìm kiếm AI")
                .build();
    }

    // API test tạo vector cho 1 ảnh cụ thể (dùng để test): POST /api/search/generate-vector/{id}
    @PostMapping("/generate-vector/{costumeImageId}")
    public ApiResponse<Void> generateVector(@PathVariable Integer costumeImageId) {
        aiService.generateAndSaveVector(costumeImageId);
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
}