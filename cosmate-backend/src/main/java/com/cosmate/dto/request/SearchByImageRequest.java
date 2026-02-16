package com.cosmate.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SearchByImageRequest {
    private MultipartFile file;
    private String text;
}