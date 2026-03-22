package com.cosmate.service;

import com.cosmate.dto.response.ImageResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface CostumeImageService {
    List<ImageResponse> getByCostumeId(Integer costumeId);

    // Đổi tên hàm và kiểu trả về để phù hợp logic upload hàng loạt
    List<ImageResponse> uploadImages(Integer costumeId, List<MultipartFile> files, String type);

    ImageResponse getById(Integer id);
    void deleteImage(Integer id);
}