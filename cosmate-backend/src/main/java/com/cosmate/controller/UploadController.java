package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.FirebaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FirebaseStorageService firebaseStorageService;

    @PostMapping("")
    public ResponseEntity<ApiResponse<String>> upload(@RequestParam("file") MultipartFile file) {
        // organize path by date or folder; here we use original filename
        String destination = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String url = firebaseStorageService.uploadFile(file, destination);
        ApiResponse<String> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(url);
        return ResponseEntity.ok(api);
    }
}
