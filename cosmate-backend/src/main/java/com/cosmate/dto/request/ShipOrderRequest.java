package com.cosmate.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ShipOrderRequest {
    private String trackingCode;
    private List<String> imageUrls; // at least one
    private List<String> notes; // optional, same length as imageUrls or null
}

