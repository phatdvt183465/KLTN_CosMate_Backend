package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomAnswerRequest {

    @NotBlank(message = "Thiếu mã câu hỏi")
    private String questionId;      // Thêm cái này để lưu vết vào Database

    @NotBlank(message = "QUESTION_CONTEXT_INVALID")
    @Size(max = 4000)
    private String questionContext; // Nội dung câu hỏi hiện tại để mớm cho AI

    @NotBlank(message = "USER_ANSWER_INVALID")
    @Size(max = 4000)
    private String userAnswer;      // Câu trả lời tự nhập của người dùng
}