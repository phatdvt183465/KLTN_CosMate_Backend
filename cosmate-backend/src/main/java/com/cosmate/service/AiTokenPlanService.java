package com.cosmate.service;

import com.cosmate.dto.request.AiTokenPlanRequest;
import com.cosmate.dto.response.AiTokenPlanResponse;

import java.util.List;

public interface AiTokenPlanService {
    AiTokenPlanResponse create(AiTokenPlanRequest req);
    AiTokenPlanResponse update(Integer id, AiTokenPlanRequest req);
    void deactivate(Integer id);
    List<AiTokenPlanResponse> getAll();
    AiTokenPlanResponse getById(Integer id);
}

