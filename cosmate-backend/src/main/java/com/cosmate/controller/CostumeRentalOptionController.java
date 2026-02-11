package com.cosmate.controller;

import com.cosmate.dto.request.RentalOptionRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.service.CostumeRentalOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rental-options")
@RequiredArgsConstructor
public class CostumeRentalOptionController {

    private final CostumeRentalOptionService rentalOptionService;

    @GetMapping("/costume/{costumeId}")
    public ApiResponse<List<CostumeResponse.RentalOptionResponse>> getByCostumeId(@PathVariable Integer costumeId) {
        return ApiResponse.<List<CostumeResponse.RentalOptionResponse>>builder()
                .result(rentalOptionService.getByCostumeId(costumeId))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CostumeResponse.RentalOptionResponse> getById(@PathVariable Integer id) {
        return ApiResponse.<CostumeResponse.RentalOptionResponse>builder()
                .result(rentalOptionService.getById(id))
                .build();
    }

    @PostMapping("/costume/{costumeId}")
    public ApiResponse<CostumeResponse.RentalOptionResponse> create(
            @PathVariable Integer costumeId,
            @RequestBody RentalOptionRequest request) {
        return ApiResponse.<CostumeResponse.RentalOptionResponse>builder()
                .code(1000)
                .message("Tạo gói thuê thành công!")
                .result(rentalOptionService.create(costumeId, request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<CostumeResponse.RentalOptionResponse> update(
            @PathVariable Integer id,
            @RequestBody RentalOptionRequest request) {
        return ApiResponse.<CostumeResponse.RentalOptionResponse>builder()
                .code(1000)
                .message("Cập nhật gói thuê thành công!")
                .result(rentalOptionService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        rentalOptionService.delete(id);
        return ApiResponse.<Void>builder()
                .message("Xóa gói thuê thành công!")
                .build();
    }
}