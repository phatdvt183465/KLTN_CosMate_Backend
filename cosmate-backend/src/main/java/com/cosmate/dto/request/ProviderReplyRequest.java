package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProviderReplyRequest {

    @NotBlank(message = "Reply content must not be blank")
    @Size(max = 1000, message = "Reply must be at most 1000 characters")
    private String replyContent;
}

