package com.cosmate.dto.response;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PoseScoringResponse {
    private int score;          // Điểm số (0 - 100)
    private String comment;     // Lời nhận xét của AI
}