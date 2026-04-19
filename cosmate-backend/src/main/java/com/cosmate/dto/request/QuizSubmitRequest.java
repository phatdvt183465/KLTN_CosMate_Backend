package com.cosmate.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class QuizSubmitRequest {
    private List<StaticAnswer> staticAnswers;
    private List<CustomAnswerRequest> customAnswers;

    @Data
    public static class StaticAnswer {
        private String questionId;
        private int scoreE;
        private int scoreA;
        private int scoreO;
    }
}