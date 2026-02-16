package com.cosmate.controller;

import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.SearchResponse;
import com.cosmate.service.AISearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class AISearchController {

    private final AISearchService aiSearchService;

    // API tìm kiếm: POST /api/search/ai
    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<SearchResponse>> searchByAI(@ModelAttribute SearchByImageRequest request) {
        return ApiResponse.<List<SearchResponse>>builder()
                .result(aiSearchService.searchSimilarCostumes(request))
                .message("Kết quả tìm kiếm AI")
                .build();
    }

    // API test tạo vector cho 1 ảnh cụ thể (dùng để test): POST /api/search/generate-vector/{id}
    @PostMapping("/generate-vector/{costumeImageId}")
    public ApiResponse<Void> generateVector(@PathVariable Integer costumeImageId) {
        aiSearchService.generateAndSaveVector(costumeImageId);
        return ApiResponse.<Void>builder().message("Đã tạo vector thành công!").build();
    }
}