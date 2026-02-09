package com.cosmate.service;

import com.cosmate.dto.response.ImageResponse; // Import DTO
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface CostumeImageService {
    List<ImageResponse> getByCostumeId(Integer costumeId);
    ImageResponse uploadImage(Integer costumeId, MultipartFile file, String type);
}