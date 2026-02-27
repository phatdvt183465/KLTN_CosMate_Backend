package com.cosmate.dto.request;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PoseScoringRequest {
    private MultipartFile image;       // Ảnh user tự chụp
    private String characterName;      // Tên nhân vật cosplay (VD: "Naruto")
}