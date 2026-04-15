package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class CustomAnswerResponse {
    private boolean isValid;
    private String reason;
    private Map<String, Integer> scores; // Chứa {"E": 1, "A": -1, "O": 2}
}