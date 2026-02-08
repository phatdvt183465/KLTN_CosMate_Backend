package com.cosmate.service;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import java.util.List;

public interface CostumeService {
    CostumeResponse createCostume(CostumeRequest request);
    List<CostumeResponse> getAllCostumes();
    CostumeResponse getById(Long id);
    // Thêm 2 dòng này
    CostumeResponse updateCostume(Long id, CostumeRequest request);
    void deleteCostume(Long id);
}