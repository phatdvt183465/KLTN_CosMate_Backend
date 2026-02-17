package com.cosmate.service;

import com.cosmate.dto.request.RecommendationRequest;
import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.SearchResponse;

import java.util.List;

public interface AISearchService {
    // Tìm kiếm bằng Vector
    List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request);

    // Tạo Vector cho ảnh
    void generateAndSaveVector(Integer costumeImageId);

    // Gợi ý Cosplay theo sở thích
    List<SearchResponse> recommendCosplay(RecommendationRequest request);
}