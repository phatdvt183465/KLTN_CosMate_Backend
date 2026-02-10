package com.cosmate.controller;

import com.cosmate.dto.request.SurchargeRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.SurchargeResponse;
import com.cosmate.service.CostumeSurchargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/surcharges")
@RequiredArgsConstructor
public class CostumeSurchargeController {

    private final CostumeSurchargeService surchargeService;

    @GetMapping("/{id}")
    public ApiResponse<SurchargeResponse> getById(@PathVariable Integer id) {
        return ApiResponse.<SurchargeResponse>builder()
                .result(surchargeService.getById(id))
                .build();
    }

    @GetMapping("/costume/{costumeId}")
    public ApiResponse<List<SurchargeResponse>> getByCostumeId(@PathVariable Integer costumeId) {
        return ApiResponse.<List<SurchargeResponse>>builder()
                .result(surchargeService.getByCostumeId(costumeId))
                .build();
    }

    @PostMapping("/costume/{costumeId}")
    public ApiResponse<SurchargeResponse> create(
            @PathVariable Integer costumeId,
            @RequestBody SurchargeRequest request) {
        return ApiResponse.<SurchargeResponse>builder()
                .code(1000)
                .message("Tạo phụ phí thành công!")
                .result(surchargeService.create(costumeId, request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<SurchargeResponse> update(
            @PathVariable Integer id,
            @RequestBody SurchargeRequest request) {
        return ApiResponse.<SurchargeResponse>builder()
                .code(1000)
                .message("Cập nhật phụ phí thành công!")
                .result(surchargeService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        surchargeService.deleteSurcharge(id);
        return ApiResponse.<Void>builder()
                .message("Xóa phụ phí!")
                .build();
    }
}