package com.cosmate.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    VNPAY("VNPAY"),
    MOMO("MOMO");

    private final String code;

    PaymentMethod(String code) { this.code = code; }

    @JsonValue
    public String getCode() { return code; }

    @JsonCreator
    public static PaymentMethod from(String value) {
        if (value == null) return null;
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
