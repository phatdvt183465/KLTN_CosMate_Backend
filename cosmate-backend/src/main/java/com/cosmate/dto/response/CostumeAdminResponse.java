package com.cosmate.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CostumeAdminResponse extends CostumeResponse {
    private String providerName;
}
