package com.cosmate.dto.request;

import lombok.Data;

@Data
public class PoseFeedbackRequest {
    private Integer poseScoreId;
    private String feedbackText;
}
