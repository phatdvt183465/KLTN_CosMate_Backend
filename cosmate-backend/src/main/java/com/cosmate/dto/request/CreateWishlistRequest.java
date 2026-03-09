package com.cosmate.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWishlistRequest {
    @NotNull
    private Integer costumeId;
}

