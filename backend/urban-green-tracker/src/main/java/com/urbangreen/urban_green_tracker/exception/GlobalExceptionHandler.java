package com.urbangreen.urban_green_tracker.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;



@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ApiResponse<?>> buildResponse(ErrorCode errorCode) {
        ApiResponse<?> response = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }
    private ResponseEntity<ApiResponse<?>> buildResponse(ErrorCode errorCode, String customMessage) {
        ApiResponse<?> response = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(customMessage)
                .build();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    // 1 all fallback exception handler
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> hanldleAllExceptions(Exception ex){
        log.error("Unhandled exception :", ex);
        return buildResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    // 2 handle my custom exception
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> handleAppException(AppException ex) {
        return buildResponse(ex.getErrorCode());
    }

    // 3 authentication exception handler
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(AuthenticationException ex) {
        return buildResponse(ErrorCode.UNAUTHENTICATED);
    }

    // 4 access denied exception handler
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildResponse(ErrorCode.UNAUTHORIZED_ACCESS);
    }

    //5 validation exception handler
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.INVALID_REQUEST.getMessage();
        return buildResponse(ErrorCode.INVALID_REQUEST, message);

    }



}
