package com.cosmate.service.impl;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    public List<ImageResponse> uploadImages(Integer costumeId, List<MultipartFile> files, String type) {
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy trang phục ID " + costumeId));

        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Kiểm duyệt nội dung 18+ (Gom lô)
        aiService.validateMultipleImageContents(files);

        // 2. Cập nhật lại vector nhúng (embedding) của bộ đồ khi có ảnh mới
        String hiddenTags = aiService.extractFeaturesFromMultipleImages(files);
        String textForVector = costume.getName() + " " + costume.getDescription() + " " + hiddenTags;
        String costumeVector = aiService.generateVectorForText(textForVector);

        costume.setCostumeVector(costumeVector);
        costumeRepository.save(costume); // Save lại Costume để cập nhật Vector mới vào DB

        // 3. Xử lý đa luồng tải ảnh lên Firebase
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(files.size(), 10));
        List<CompletableFuture<CostumeImage>> futures = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            CompletableFuture<CostumeImage> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String original = file.getOriginalFilename();
                    String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String path = String.format("costumes/%d/%d_%s", costume.getId(), System.currentTimeMillis(), safeName);

                    String imageUrl = firebaseStorageService.uploadFile(file, path);

                    CostumeImage img = new CostumeImage();
                    img.setImageUrl(imageUrl);
                    img.setType((type != null && !type.isEmpty()) ? type : "DETAIL");
                    img.setCostume(costume);

                    return img;
                } catch (Exception e) {
                    throw new RuntimeException("Upload file ảnh thất bại: " + e.getMessage());
                }
            }, executor);

            futures.add(future);
        }

        // 4. Lưu toàn bộ kết quả xuống Database
        List<CostumeImage> savedImages = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<CostumeImage> future : futures) {
                savedImages.add(future.get());
            }
            // Batch save vào DB để tối ưu truy vấn
            imageRepository.saveAll(savedImages);

        } catch (Exception e) {
            throw new RuntimeException("Lỗi trong quá trình lưu trữ ảnh bổ sung: " + e.getCause().getMessage(), e);
        } finally {
            executor.shutdown();
        }

        // 5. Build danh sách DTO trả về cho Client
        return savedImages.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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