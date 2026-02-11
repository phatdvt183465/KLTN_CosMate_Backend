package com.cosmate.service;

import com.cosmate.dto.request.RentalOptionRequest;
import com.cosmate.dto.response.CostumeResponse;
import java.util.List;

public interface CostumeRentalOptionService {
    List<CostumeResponse.RentalOptionResponse> getByCostumeId(Integer costumeId);
    CostumeResponse.RentalOptionResponse getById(Integer id);
    CostumeResponse.RentalOptionResponse create(Integer costumeId, RentalOptionRequest request);
    CostumeResponse.RentalOptionResponse update(Integer id, RentalOptionRequest request);
    void delete(Integer id);
}