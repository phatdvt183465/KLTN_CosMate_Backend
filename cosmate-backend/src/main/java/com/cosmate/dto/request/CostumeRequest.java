package com.cosmate.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CostumeRequest {
    private String name;
    private String description;
    private String size;
    private String rentPurpose;
    private Integer numberOfItems;
    private BigDecimal pricePerDay;
    private BigDecimal depositAmount;
    private Long providerId;
    private List<MultipartFile> imageFiles;
    private String surcharges;
}