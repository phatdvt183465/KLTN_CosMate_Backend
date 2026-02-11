package com.cosmate.service;

import com.cosmate.dto.request.SurchargeRequest;
import com.cosmate.dto.response.SurchargeResponse;
import java.util.List;

public interface CostumeSurchargeService {
    List<SurchargeResponse> getByCostumeId(Integer costumeId);
    SurchargeResponse create(Integer costumeId, SurchargeRequest request);
    SurchargeResponse update(Integer id, SurchargeRequest request);
    SurchargeResponse getById(Integer id);
    void deleteSurcharge(Integer id);
}