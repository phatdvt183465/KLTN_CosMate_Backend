package com.cosmate.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ServiceRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private Integer providerId;
    private String areas;
    private List<MultipartFile> albumFiles; // List file ảnh thật để up Firebase
}