package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PoseScoringRequest {
    @NotNull(message = "IMAGE_INVALID")
    private MultipartFile image;       // Ảnh user tự chụp

    private MultipartFile referenceImage; // Ảnh gốc để AI đối chiếu (Tùy chọn)

    @NotBlank(message = "CHARACTER_NAME_INVALID")
    @Size(max = 255)
    private String characterName;      // Tên nhân vật cosplay (VD: "Naruto")
}
