package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ImageResponse;
import com.cosmate.service.CostumeImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class CostumeImageController {

    private final CostumeImageService imageService;

    @GetMapping("/{id}")
    public ApiResponse<ImageResponse> getById(@PathVariable Integer id) {
        return ApiResponse.<ImageResponse>builder()
                .result(imageService.getById(id))
                .build();
    }

    @GetMapping("/costume/{costumeId}")
    public ApiResponse<List<ImageResponse>> getByCostumeId(@PathVariable Integer costumeId) {
        return ApiResponse.<List<ImageResponse>>builder()
                .result(imageService.getByCostumeId(costumeId))
                .build();
    }

    // [UPDATED] Hỗ trợ upload nhiều ảnh cùng lúc cho tính năng bổ sung ảnh
    @PostMapping(value = "/costume/{costumeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<ImageResponse>> uploadImages(
            @PathVariable Integer costumeId,
            @RequestParam("files") List<MultipartFile> files, // Nhận mảng files
            @RequestParam(value = "type", required = false) String type) {

        return ApiResponse.<List<ImageResponse>>builder()
                .code(1000)
                .message("Upload hàng loạt ảnh thành công!")
                .result(imageService.uploadImages(costumeId, files, type)) // Gọi hàm uploadImages mới
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        imageService.deleteImage(id);
        return ApiResponse.<Void>builder()
                .message("Xóa ảnh thành công!")
                .build();
    }
}