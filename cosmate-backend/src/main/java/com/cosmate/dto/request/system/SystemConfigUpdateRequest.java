package com.cosmate.dto.request.system;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigUpdateRequest {
    @NotBlank(message = "Value cannot be blank")
    private String configValue;
}
