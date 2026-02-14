package com.cosmate.dto.request;

import lombok.Data;

@Data
public class CreateReviewRequest {
    private Integer orderId;
    private Integer rating;
    private String comment;
}

