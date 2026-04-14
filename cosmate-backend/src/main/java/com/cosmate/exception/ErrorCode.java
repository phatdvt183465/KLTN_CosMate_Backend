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
    ACCOUNT_NOT_ACTIVATED(1008, "Tài khoản chưa được kích hoạt!"),
    INVALID_USERNAME(1009, "Tên người dùng không hợp lệ!"),
    INVALID_PASSWORD(1009, "Mật khẩu không hợp lệ!"),
    INVALID_ROLE(1010, "Vai trò không hợp lệ!"),
    USER_NOT_FOUND(1011, "Không tìm thấy người dùng!"),
    INVALID_EMAIL(1012, "Email không hợp lệ!"),
    PROVIDER_NOT_FOUND(1013, "Không tìm thấy provider cho user_id!"),
    WISHLIST_NOT_FOUND(1014, "Không tìm thấy mục wishlist!"),
    UNAUTHORIZED(1015, "Chưa xác thực - Vui lòng đăng nhập"),
    COSTUME_NOT_FOUND(1016, "Không tìm thấy trang phục!"),
    COSTUME_DELETED(1017, "Trang phục đã bị xóa!"),
    INVALID_COSTUME_REQUEST(1018, "Dữ liệu trang phục không hợp lệ!"),
    INVALID_COSTUME_STATUS(1019, "Trạng thái trang phục không hợp lệ!"),
    ACCESSORY_NOT_FOUND(1020, "Không tìm thấy phụ kiện!"),
    RENTAL_OPTION_NOT_FOUND(1021, "Không tìm thấy gói thuê!"),
    SURCHARGE_NOT_FOUND(1022, "Không tìm thấy phụ phí!"),
    IMAGE_NOT_FOUND(1023, "Không tìm thấy ảnh!");

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
