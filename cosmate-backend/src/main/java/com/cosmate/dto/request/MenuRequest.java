package com.cosmate.dto.request;

import lombok.Data;

@Data
public class MenuRequest {
    private String name;
    private String description;
    private Integer displayOrder;
}