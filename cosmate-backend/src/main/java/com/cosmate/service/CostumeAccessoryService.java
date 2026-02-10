package com.cosmate.service;

import com.cosmate.dto.request.AccessoryRequest;
import com.cosmate.dto.response.CostumeResponse;
import java.util.List;

public interface CostumeAccessoryService {
    List<CostumeResponse.AccessoryResponse> getByCostumeId(Integer costumeId);
    CostumeResponse.AccessoryResponse getById(Integer id);
    CostumeResponse.AccessoryResponse create(Integer costumeId, AccessoryRequest request);
    CostumeResponse.AccessoryResponse update(Integer id, AccessoryRequest request);
    void delete(Integer id);
}