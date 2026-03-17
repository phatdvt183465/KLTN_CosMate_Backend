package com.cosmate.dto.filter;

import com.cosmate.base.crud.dto.FilterOperator;
import com.cosmate.base.spec.FilterField;
import lombok.Data;

@Data
public class UserFilter {
    @FilterField(operator = FilterOperator.LIKE, ignoreCase = true)
    private String username;

    @FilterField(operator = FilterOperator.LIKE, ignoreCase = true)
    private String email;

    @FilterField(operator = FilterOperator.EQUAL)
    private String status;
}