package com.cosmate.service;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import java.util.List;

public interface CostumeService {
    CostumeResponse createCostume(CostumeRequest request);
    List<CostumeResponse> getAllCostumes();
    CostumeResponse getById(Integer id);
    CostumeResponse updateCostume(Integer id, CostumeRequest request);
    void deleteCostume(Integer id);
    void changeStatus(Integer id, String newStatus);
    List<CostumeResponse> getByProviderId(Integer providerId);
    List<CostumeResponse> searchCostumes(String keyword);
}