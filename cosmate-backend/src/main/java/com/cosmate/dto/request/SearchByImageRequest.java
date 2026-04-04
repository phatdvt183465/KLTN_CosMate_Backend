package com.cosmate.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class SearchByImageRequest {
    private List<MultipartFile> files;
    private String text;
}