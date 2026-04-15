package com.cosmate.service;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import java.util.List;

public interface CostumeService {
    CostumeResponse createCostume(Integer currentUserId, CostumeRequest request);
    List<CostumeResponse> getAllCostumes();
    CostumeResponse getById(Integer id);
    CostumeResponse updateCostume(Integer currentUserId, Integer id, CostumeRequest request);
    void deleteCostume(Integer currentUserId, Integer id);
    void changeStatus(Integer currentUserId, Integer id, String newStatus);
    List<CostumeResponse> getByProviderId(Integer providerId);
    List<CostumeResponse> searchCostumes(String keyword);
}