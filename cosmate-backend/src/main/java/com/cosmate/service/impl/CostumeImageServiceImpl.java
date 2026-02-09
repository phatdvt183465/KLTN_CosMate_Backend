package com.cosmate.service.impl;

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

@Service
@RequiredArgsConstructor
public class CostumeImageServiceImpl implements CostumeImageService {

    private final CostumeImageRepository imageRepository;
    private final CostumeRepository costumeRepository;
    // private final FirebaseService firebaseService;

    @Override
    public List<CostumeImage> getByCostumeId(Integer costumeId) {
        // Trả về danh sách ảnh của costume
        return imageRepository.findByCostumeId(costumeId);
    }

    @Override
    @Transactional
    public CostumeImage uploadImage(Integer costumeId, MultipartFile file, String type) {
        // Kiểm tra xem bộ đồ có tồn tại không
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bộ đồ ID: " + costumeId + " để thêm ảnh!"));

        // Giả lập logic upload Firebase (giống như mình đã thống nhất)
        // String imageUrl = firebaseService.upload(file);
        String imageUrl = "https://firebase-storage/cosmate/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        CostumeImage img = new CostumeImage();
        img.setImageUrl(imageUrl);
        // Nếu không truyền type thì mặc định là DETAIL
        img.setType(type != null ? type : "DETAIL");
        img.setCostume(costume);

        return imageRepository.save(img);
    }
}