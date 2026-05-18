package com.cosmate.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class SearchByImageRequest {
    private List<MultipartFile> files;

    @Size(max = 500)
    private String text;
}
