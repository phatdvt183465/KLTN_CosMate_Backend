package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomAnswerRequest {
    @NotBlank(message = "QUESTION_CONTEXT_INVALID")
    @Size(max = 4000)
    private String questionContext; // Nội dung câu hỏi hiện tại

    @NotBlank(message = "USER_ANSWER_INVALID")
    @Size(max = 4000)
    private String userAnswer;      // Câu trả lời tự nhập của người dùng
}