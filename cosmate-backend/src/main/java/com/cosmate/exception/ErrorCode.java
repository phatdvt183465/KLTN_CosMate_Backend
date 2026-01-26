package com.cosmate.exception;

public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(99999, "Lỗi không xác định!"),
    INVALID_KEY(1001, "Key không hợp lệ!"),
    ;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private int code;
    private String message;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
