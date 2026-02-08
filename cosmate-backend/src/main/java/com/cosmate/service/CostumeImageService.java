package com.cosmate.service;

import com.cosmate.entity.CostumeImage;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface CostumeImageService {
    // Lấy tất cả ảnh của một bộ đồ
    List<CostumeImage> getByCostumeId(Long costumeId);

    // Upload một ảnh mới (lên Firebase) và lưu vào DB
    CostumeImage uploadImage(Long costumeId, MultipartFile file, String type);

    // Xóa ảnh theo ID
    void delete(Long id);
}