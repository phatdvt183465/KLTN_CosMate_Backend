package com.cosmate.controller;

import com.cosmate.dto.request.AccessoryRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.service.CostumeAccessoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accessories")
@RequiredArgsConstructor
public class CostumeAccessoryController {

    private final CostumeAccessoryService accessoryService;

    @GetMapping("/costume/{costumeId}")
    public ApiResponse<List<CostumeResponse.AccessoryResponse>> getByCostume(@PathVariable Integer costumeId) {
        return ApiResponse.<List<CostumeResponse.AccessoryResponse>>builder()
                .result(accessoryService.getByCostumeId(costumeId)).build();
    }

    @PostMapping("/costume/{costumeId}")
    public ApiResponse<CostumeResponse.AccessoryResponse> create(@PathVariable Integer costumeId, @RequestBody AccessoryRequest request) {
        return ApiResponse.<CostumeResponse.AccessoryResponse>builder()
                .result(accessoryService.create(costumeId, request)).message("Thêm phụ kiện thành công").build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        accessoryService.delete(id);
        return ApiResponse.<Void>builder().message("Đã xóa phụ kiện").build();
    }
}