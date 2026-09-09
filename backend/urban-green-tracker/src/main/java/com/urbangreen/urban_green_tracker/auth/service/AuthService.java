package com.urbangreen.urban_green_tracker.auth.service;


import com.urbangreen.urban_green_tracker.auth.dto.LoginRequest;
import com.urbangreen.urban_green_tracker.auth.dto.LoginResponse;
import com.urbangreen.urban_green_tracker.exception.AppException;
import com.urbangreen.urban_green_tracker.exception.ErrorCode;
import com.urbangreen.urban_green_tracker.user.entity.User;
import com.urbangreen.urban_green_tracker.user.entity.enums.UserStatus;
import com.urbangreen.urban_green_tracker.user.repository.UserRepository;
import com.urbangreen.urban_green_tracker.auth.dto.RefreshTokenRequest;
import java.sql.Ref;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;


import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    LoginAttemptService loginAttemptService;

    TokenService tokenService;
    RefreshTokenService refreshTokenService;

    static String DUMMY_HASH = "$2a$10$wK/p4z2u.89p4z2u.89p4ezwG.wK/p4z2u.89p4z2u.89p4ezwG.";

    public LoginResponse authenticate(LoginRequest request, String clientIp) {
        String identifier = request.getIdentifier().trim();

        if (loginAttemptService.isBlocked(clientIp, identifier)) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElse(null);

        boolean isPasswordMatch = false;

        if (user != null) {
            isPasswordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } else {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
        }
        if (user == null || !isPasswordMatch) {
            loginAttemptService.loginFailed(clientIp, identifier);
            log.warn("Login failed for identifier: {} from IP: {}", identifier, clientIp);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        loginAttemptService.loginSucceeded(clientIp, identifier);
        log.info("User [{}] login successfully from IP: {}", identifier, clientIp);

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken("mock-refresh-token-waiting-for-phase-13") 
                .build();
    }

    public LoginResponse refreshToken(String refreshTokenString) {
        String refreshToken = refreshTokenString.trim();
        var userId = refreshTokenService.validateAndRotate(refreshToken);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if(user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        if(user.getStatus() == UserStatus.DISABLED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        String accessToken = tokenService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.generateRefreshToken(user.getId());
        
        log.info("Refresh token for user [{}] has been successfully refreshed.", user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .build();
    }


    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            tokenService.blacklistAccessToken(accessToken.substring(7));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
    }

    public void logoutAllDevices(String accessToken, UUID currentUserId) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            tokenService.blacklistAccessToken(accessToken.substring(7));
        }
        refreshTokenService.revokeAllUserSessions(currentUserId);
    }

}
