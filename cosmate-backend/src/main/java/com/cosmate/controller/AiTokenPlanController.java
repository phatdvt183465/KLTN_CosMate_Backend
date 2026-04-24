package com.cosmate.controller;

import com.cosmate.dto.request.AiTokenPlanRequest;
import com.cosmate.dto.response.AiTokenPlanResponse;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.AiTokenPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-token-plans")
@RequiredArgsConstructor
public class AiTokenPlanController {

    private final AiTokenPlanService service;

    @PostMapping
    public ResponseEntity<ApiResponse<AiTokenPlanResponse>> create(@RequestBody AiTokenPlanRequest req) {
        AiTokenPlanResponse resp = service.create(req);
        ApiResponse<AiTokenPlanResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AiTokenPlanResponse>> update(@PathVariable Integer id, @RequestBody AiTokenPlanRequest req) {
        AiTokenPlanResponse resp = service.update(id, req);
        ApiResponse<AiTokenPlanResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.deactivate(id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiTokenPlanResponse>>> getAll() {
        List<AiTokenPlanResponse> list = service.getAll();
        ApiResponse<List<AiTokenPlanResponse>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(list);
        return ResponseEntity.ok(api);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiTokenPlanResponse>> getById(@PathVariable Integer id) {
        AiTokenPlanResponse resp = service.getById(id);
        ApiResponse<AiTokenPlanResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }
}


