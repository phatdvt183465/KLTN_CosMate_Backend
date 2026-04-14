package com.cosmate.controller;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.service.CostumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/costumes")
@RequiredArgsConstructor
public class CostumeController {

    private final CostumeService costumeService;

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            }
            if (principal instanceof Integer) return (Integer) principal;
            if (principal instanceof Long) return ((Long) principal).intValue();
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/provider/{providerId}")
    public ApiResponse<List<CostumeResponse>> getByProviderId(@PathVariable Integer providerId) {
        return ApiResponse.<List<CostumeResponse>>builder()
                .result(costumeService.getByProviderId(providerId))
                .build();
    }

    // Get all costumes (excluding deleted ones)
    @GetMapping
    public ApiResponse<List<CostumeResponse>> getAll() {
        return ApiResponse.<List<CostumeResponse>>builder()
                .result(costumeService.getAllCostumes())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CostumeResponse> getById(@PathVariable Integer id) {
        return ApiResponse.<CostumeResponse>builder()
                .result(costumeService.getById(id))
                .build();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CostumeResponse> create(@ModelAttribute CostumeRequest request) {
        return ApiResponse.<CostumeResponse>builder()
                .result(costumeService.createCostume(getCurrentUserId(), request))
                .message("Tạo mới thành công!")
                .build();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CostumeResponse> update(@PathVariable Integer id, @ModelAttribute CostumeRequest request) {
        return ApiResponse.<CostumeResponse>builder()
                .result(costumeService.updateCostume(getCurrentUserId(), id, request))
                .message("Cập nhật xong!")
                .build();
    }

    // API đổi trạng thái: DISABLED <-> AVAILABLE
    // Gọi: PUT /api/costumes/{id}/status?status=DISABLED
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(
            @PathVariable Integer id,
            @RequestParam String status) { // Truyền status muốn đổi vào đây

        costumeService.changeStatus(getCurrentUserId(), id, status);
        return ApiResponse.<Void>builder()
                .message("Đã đổi trạng thái thành " + status + " thành công!")
                .build();
    }

    // Thêm vào CostumeController.java
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        costumeService.deleteCostume(getCurrentUserId(), id);
        return ApiResponse.<Void>builder()
                .message("Xóa bộ đồ thành công!")
                .build();
    }

    // API tìm kiếm chủ động: GET /api/costumes/search?keyword=naruto
    @GetMapping("/search")
    public ApiResponse<List<CostumeResponse>> searchCostumes(@RequestParam String keyword) {
        return ApiResponse.<List<CostumeResponse>>builder()
                .result(costumeService.searchCostumes(keyword)) // Anh tự thêm hàm này vào Service gọi repo nhé
                .build();
    }
}