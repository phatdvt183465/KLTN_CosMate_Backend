package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomAnswerResponse {
    private boolean isValid;
    private String reason;
    private Map<String, Integer> scores; // Chứa {"E": 1, "A": -1, "O": 2}
}