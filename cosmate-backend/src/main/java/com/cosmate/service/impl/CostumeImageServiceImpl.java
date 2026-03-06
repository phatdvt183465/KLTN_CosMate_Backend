package com.cosmate.service.impl;

import com.google.cloud.storage.Bucket;
import com.cosmate.dto.response.ImageResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeImage;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.service.AIService;
import com.cosmate.service.CostumeImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeImageServiceImpl implements CostumeImageService {

    private final CostumeImageRepository imageRepository;
    private final CostumeRepository costumeRepository;
    private final AIService aiService;
    private final com.cosmate.service.FirebaseStorageService firebaseStorageService;

    @Override
    public List<ImageResponse> getByCostumeId(Integer costumeId) {
        if (!costumeRepository.existsById(costumeId)) {
            throw new RuntimeException("Error: Costume ID " + costumeId + " not found.");
        }
        return imageRepository.findByCostumeId(costumeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ImageResponse uploadImage(Integer costumeId, MultipartFile file, String type) {
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Error: Costume ID " + costumeId + " not found."));

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Error: File is empty.");
        }

        // 1. Check AI (18+)
        aiService.validateImageContent(file);

        try {
            // Dọn dẹp tên file
            String original = file.getOriginalFilename();
            String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");

            // Build path: costumes/{costumeId}/{timestamp}_{safeName}
            String path = String.format("costumes/%d/%d_%s", costume.getId(), System.currentTimeMillis(), safeName);

            // Upload lấy link public
            String imageUrl = firebaseStorageService.uploadFile(file, path);

            CostumeImage img = new CostumeImage();
            img.setImageUrl(imageUrl);
            img.setType((type != null && !type.isEmpty()) ? type : "DETAIL");
            img.setCostume(costume);

            return mapToResponse(imageRepository.save(img));

        } catch (Exception e) {
            throw new RuntimeException("Upload ảnh lẻ xịt rồi: " + e.getMessage());
        }
    }

    @Override
    public ImageResponse getById(Integer id) {
        return mapToResponse(imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Image ID " + id + " not found.")));
    }

    @Override
    @Transactional
    public void deleteImage(Integer id) {
        if (!imageRepository.existsById(id)) {
            throw new RuntimeException("Error: Image not found to delete.");
        }
        imageRepository.deleteById(id);
    }

    private ImageResponse mapToResponse(CostumeImage entity) {
        return ImageResponse.builder()
                .id(entity.getId())
                .costumeId(entity.getCostume().getId())
                .imageUrl(entity.getImageUrl())
                .type(entity.getType())
                .build();
    }
}