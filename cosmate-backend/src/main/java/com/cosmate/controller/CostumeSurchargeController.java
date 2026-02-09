package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.CostumeSurcharge;
import com.cosmate.service.CostumeSurchargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/surcharges")
@RequiredArgsConstructor
public class CostumeSurchargeController {

    private final CostumeSurchargeService surchargeService;

    @GetMapping("/costume/{costumeId}")
    public ApiResponse<List<CostumeSurcharge>> getByCostumeId(@PathVariable Integer costumeId) {
        return ApiResponse.<List<CostumeSurcharge>>builder()
                .result(surchargeService.getByCostumeId(costumeId))
                .build();
    }

    @PostMapping("/costume/{costumeId}")
    public ApiResponse<CostumeSurcharge> create(@PathVariable Integer costumeId, @RequestBody CostumeSurcharge request) {
        return ApiResponse.<CostumeSurcharge>builder()
                .code(1000)
                .message("Created surcharge successfully")
                .result(surchargeService.create(costumeId, request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<CostumeSurcharge> update(@PathVariable Integer id, @RequestBody CostumeSurcharge request) {
        return ApiResponse.<CostumeSurcharge>builder()
                .code(1000)
                .message("Updated surcharge successfully")
                .result(surchargeService.update(id, request))
                .build();
    }
}