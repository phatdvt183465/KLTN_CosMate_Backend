package com.cosmate.exception;

public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(99999, "Lỗi không xác định!"),
    INVALID_KEY(1001, "Key không hợp lệ!"),
    INVALID_PHONE(1002, "Số điện thoại không hợp lệ!"),
    EMAIL_ALREADY_EXISTS(1003, "Email đã tồn tại!"),
    USERNAME_ALREADY_EXISTS(1004, "Tên người dùng đã tồn tại!"),
    INVALID_CREDENTIALS(1005, "Thông tin đăng nhập không chính xác!"),
    FORBIDDEN(1006, "Không có quyền thực hiện thao tác này!"),
    ACCOUNT_BANNED(1007, "Tài khoản đã bị khóa (BANNED)!"),
    INVALID_USERNAME(1008, "Tên người dùng không hợp lệ!"),
    INVALID_PASSWORD(1009, "Mật khẩu không hợp lệ!"),
    INVALID_ROLE(1010, "Vai trò không hợp lệ!"),
    USER_NOT_FOUND(1011, "Không tìm thấy người dùng!"),
    INVALID_EMAIL(1012, "Email không hợp lệ!"),
    PROVIDER_NOT_FOUND(1013, "Không tìm thấy provider cho user_id!"),
    WISHLIST_NOT_FOUND(1014, "Không tìm thấy mục wishlist!");

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
