package com.cosmate.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewResponse {
    private Integer id;
    private Integer orderId;
    private String username;
    private String avatarUrl;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private List<ReviewUrlResponse> images;
}

