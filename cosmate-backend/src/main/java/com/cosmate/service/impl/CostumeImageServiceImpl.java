package com.cosmate.service.impl;

import com.cosmate.dto.response.ImageResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeImage;
import com.cosmate.repository.CostumeImageRepository;
import com.cosmate.repository.CostumeRepository;
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
    // private final FirebaseService firebaseService;

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

        // Mock Firebase Upload
        // String url = firebaseService.upload(file);
        String url = "https://firebase-storage/cosmate/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        CostumeImage img = new CostumeImage();
        img.setImageUrl(url);
        // Default to DETAIL if type is null or empty
        img.setType((type != null && !type.isEmpty()) ? type : "DETAIL");
        img.setCostume(costume);

        return mapToResponse(imageRepository.save(img));
    }

    // Mapper helper
    private ImageResponse mapToResponse(CostumeImage entity) {
        return ImageResponse.builder()
                .id(entity.getId())
                .costumeId(entity.getCostume().getId())
                .imageUrl(entity.getImageUrl())
                .type(entity.getType())
                .build();
    }
}