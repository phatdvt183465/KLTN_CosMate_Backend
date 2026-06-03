package com.cosmate.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReviewRequest {
    private Integer orderId;
    private Integer rating;
    @Size(max = 1000, message = "Đánh giá không được vượt quá 1000 ký tự")
    private String comment;
}

