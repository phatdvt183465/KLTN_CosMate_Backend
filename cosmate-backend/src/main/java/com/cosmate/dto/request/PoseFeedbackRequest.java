package com.cosmate.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PoseFeedbackRequest {
    private Integer poseScoreId;
    @Size(max = 800, message = "Phản hồi không được vượt quá 800 ký tự")
    private String feedbackText;
}
