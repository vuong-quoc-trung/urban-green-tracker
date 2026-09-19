package com.urbangreen.urban_green_tracker.auth.controller;

import com.urbangreen.urban_green_tracker.auth.dto.LoginRequest;
import com.urbangreen.urban_green_tracker.auth.dto.LoginResponse;
import com.urbangreen.urban_green_tracker.auth.dto.RegisterRequest;
import com.urbangreen.urban_green_tracker.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthService authService;
    private final long REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.authenticate(request, clientIp);

        // Store the refresh token in an HttpOnly cookie
        ResponseCookie cookie = buildCookie(response.getRefreshToken(), REFRESH_TOKEN_EXPIRY);

        // Remove the refresh token from the response body so it is not exposed to the frontend
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");

        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }

        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress;
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            // Automatically extract the refresh token from the cookie
            @CookieValue(name = "refreshToken") String refreshToken) {

        LoginResponse response = authService.refreshToken(refreshToken);

        // Issue a new cookie to replace the old one because the token has been rotated
        ResponseCookie cookie = buildCookie(response.getRefreshToken(), REFRESH_TOKEN_EXPIRY);
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String authHeader,
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        authService.logout(authHeader, refreshToken);

        // Create an empty cookie with MaxAge = 0 to force the browser to delete the old cookie
        ResponseCookie deleteCookie = buildCookie("", 0);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Logged out successfully!");
    }

    private ResponseCookie buildCookie(String token, long maxAgeInSeconds) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)       // Prevent JavaScript from accessing the cookie
                .secure(false)        // Set to FALSE for localhost (HTTP). Must be TRUE in production (HTTPS).
                .path("/api/v1/auth/refresh") // Cookie only sent when requesting this specific path
                .maxAge(maxAgeInSeconds)
                .sameSite("Strict")   // Helps protect against CSRF attacks
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAllDevices(
            @RequestHeader("Authorization") String authHeader) {

        // Get the user ID from the authenticated user's security context
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();

        // Convert the user ID string to UUID
        UUID currentUserId = UUID.fromString(userIdStr);

        // Log out the user from all devices
        authService.logoutAllDevices(authHeader, currentUserId);

        // Delete the refresh token cookie from the current device
        ResponseCookie deleteCookie = buildCookie("", 0);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Logged out from all devices");
    }
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        LoginResponse response = authService.register(request);

        // Set the refresh token in an HttpOnly cookie, same as login
        ResponseCookie cookie = buildCookie(response.getRefreshToken(), REFRESH_TOKEN_EXPIRY);
        response.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }
}