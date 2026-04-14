package com.cosmate.exception;

import com.cosmate.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        HttpStatus status = switch (errorCode) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, ACCOUNT_BANNED -> HttpStatus.FORBIDDEN;
            case COSTUME_NOT_FOUND, COSTUME_DELETED, PROVIDER_NOT_FOUND, ACCESSORY_NOT_FOUND, RENTAL_OPTION_NOT_FOUND, SURCHARGE_NOT_FOUND, IMAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                .message(ex.getMessage() != null ? ex.getMessage() : ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
