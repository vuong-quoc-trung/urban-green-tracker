package com.urbangreen.urban_green_tracker.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {

    // ===== System =====
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== 400 - Bad Request =====
    INVALID_REQUEST(1001, "Invalid request data", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1002, "Invalid message key", HttpStatus.BAD_REQUEST),

    // ===== 401 - Unauthorized =====
    UNAUTHENTICATED(1003, "Username or password is incorrect", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(1004, "Account or IP is locked due to too many failed login attempts. Please try again after 15 minutes", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(1005, "Your account has been disabled", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(1009, "Invalid or expired token", HttpStatus.UNAUTHORIZED),

    // ===== 403 - Forbidden =====
    UNAUTHORIZED_ACCESS(1006, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),

    // ===== 404 - Not Found =====
    USER_NOT_EXISTED(1007, "User does not exist", HttpStatus.NOT_FOUND),

    // ===== 409 - Conflict =====
    USER_EXISTED(1008, "User already existed", HttpStatus.CONFLICT),

    // ===== 500 - Internal Server Error =====
    TOKEN_GENERATION_FAILED(1010, "Cannot generate token", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REFRESH_TOKEN(1011, "Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED)
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
