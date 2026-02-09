package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.CostumeImage;
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

    @GetMapping("/costume/{costumeId}")
    public ApiResponse<List<CostumeImage>> getByCostumeId(@PathVariable Integer costumeId) {
        return ApiResponse.<List<CostumeImage>>builder()
                .result(imageService.getByCostumeId(costumeId))
                .build();
    }

    @PostMapping(value = "/costume/{costumeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CostumeImage> uploadImage(
            @PathVariable Integer costumeId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.<CostumeImage>builder()
                .code(1000)
                .message("Uploaded image successfully")
                .result(imageService.uploadImage(costumeId, file, type))
                .build();
    }
}