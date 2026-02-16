package com.cosmate.service;

import com.cosmate.dto.request.SearchByImageRequest;
import com.cosmate.dto.response.SearchResponse;
import java.util.List;

public interface AISearchService {
    List<SearchResponse> searchSimilarCostumes(SearchByImageRequest request);
    void generateAndSaveVector(Integer costumeImageId);
}