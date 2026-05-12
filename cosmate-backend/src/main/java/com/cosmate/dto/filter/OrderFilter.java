package com.cosmate.dto.filter;

import lombok.Data;

@Data
public class OrderFilter {
    private String code;
    private String status;
    private String orderType;
}
