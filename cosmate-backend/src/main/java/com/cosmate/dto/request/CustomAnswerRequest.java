package com.cosmate.dto.request;

import lombok.Data;

@Data
public class CustomAnswerRequest {
    private String questionContext; // Nội dung câu hỏi hiện tại
    private String userAnswer;      // Câu trả lời tự nhập của người dùng
}