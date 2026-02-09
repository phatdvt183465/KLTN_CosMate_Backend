package com.cosmate.controller;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.service.CostumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType; // Thêm cái này
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/costumes")
@RequiredArgsConstructor
public class CostumeController {

    private final CostumeService costumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CostumeResponse> create(@ModelAttribute CostumeRequest request) {
        return ApiResponse.<CostumeResponse>builder()
                .result(costumeService.createCostume(request))
                .message("Tạo mới thành công!")
                .build();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CostumeResponse> update(@PathVariable Integer id, @ModelAttribute CostumeRequest request) {
        return ApiResponse.<CostumeResponse>builder()
                .result(costumeService.updateCostume(id, request))
                .message("Cập nhật xong!")
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CostumeResponse> getById(@PathVariable Integer id) {
        return ApiResponse.<CostumeResponse>builder()
                .result(costumeService.getById(id))
                .build();
    }

    // Thêm vào CostumeController.java
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        costumeService.deleteCostume(id);
        return ApiResponse.<Void>builder()
                .message("Xóa bộ đồ thành công (Soft Delete)!")
                .build();
    }
}