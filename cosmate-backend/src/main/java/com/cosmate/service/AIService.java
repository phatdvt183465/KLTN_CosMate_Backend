package com.cosmate.service;

import com.cosmate.dto.request.PoseScoringRequest;
import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.PoseScoringResponse;
import com.cosmate.dto.response.SearchResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AIService {
    // Tìm kiếm bằng Vector
    List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request);

    // Tạo Vector cho ảnh
    void generateAndSaveVector(Integer costumeImageId);

    // Gợi ý Cosplay theo sở thích
    List<SearchResponse> recommendCosplay(RecommendationRequest request);

    // Check vi phạm hình ảnh
    void validateImageContent(MultipartFile file);

    // Chấm điểm Pose dáng
    PoseScoringResponse scorePose(PoseScoringRequest request);
}