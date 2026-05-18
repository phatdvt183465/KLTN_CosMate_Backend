package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QrApproveRequest {
    @NotBlank
    private String sessionId;
}

