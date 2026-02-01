package com.cosmate.exception;

import com.cosmate.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse> handlingAppException(AppException exception){
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse apiResponse = new ApiResponse();

        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        logger.debug("AppException handled: {}", errorCode, exception);

        if (errorCode == ErrorCode.FORBIDDEN || errorCode == ErrorCode.ACCOUNT_BANNED) {
            // return proper 403 for forbidden or banned operations
            return ResponseEntity.status(403).body(apiResponse);
        }

        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse> handlingValidation(MethodArgumentNotValidException exception){
        logger.debug("Validation exception: {}", exception.getMessage(), exception);
        String enumKey = exception.getFieldError().getDefaultMessage();

        ErrorCode errorCode = ErrorCode.INVALID_KEY;

        try {
            switch (enumKey) {
                case "USERNAME_INVALID": errorCode = ErrorCode.INVALID_USERNAME; break;
                case "EMAIL_INVALID": errorCode = ErrorCode.INVALID_EMAIL; break;
                case "INVALID_PHONE": errorCode = ErrorCode.INVALID_PHONE; break;
                case "INVALID_PASSWORD": errorCode = ErrorCode.INVALID_PASSWORD; break;
                default:
                    try { errorCode = ErrorCode.valueOf(enumKey); } catch (IllegalArgumentException ignored) {}
            }
        } catch (IllegalArgumentException e){

        }

        ApiResponse apiResponse = new ApiResponse();

        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        logger.debug("Mapped validation error {} -> {}", enumKey, errorCode);

        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse> handlingAll(Exception exception) {
        logger.error("Unhandled exception: {}", exception.getMessage(), exception);
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setCode(99998);
        apiResponse.setMessage("Internal server error: " + exception.getMessage());
        return ResponseEntity.status(500).body(apiResponse);
    }
}
