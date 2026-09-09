package com.urbangreen.urban_green_tracker.auth.service;

import com.urbangreen.urban_green_tracker.exception.AppException;
import com.urbangreen.urban_green_tracker.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-token-expiration:604800}")
    private long refreshTokenExpiration; // seconds (default 7 days)

    public String generateRefreshToken(UUID userId) {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String hashedToken = hashToken(plainToken);

        // Store valid token
        String validKey = "auth:refresh:valid:" + hashedToken;
        redisTemplate.opsForValue().set(validKey, userId.toString(), refreshTokenExpiration, TimeUnit.SECONDS);

        // Track user sessions (multi-device support)
        String userSessionsKey = "auth:user:" + userId + ":sessions";
        redisTemplate.opsForSet().add(userSessionsKey, hashedToken);
        redisTemplate.expire(userSessionsKey, refreshTokenExpiration, TimeUnit.SECONDS);

        return plainToken;
    }

    public UUID validateAndRotate(String plainToken) {
        String hashedToken = hashToken(plainToken);
        String validKey = "auth:refresh:valid:" + hashedToken;
        String usedKey = "auth:refresh:used:" + hashedToken;

        // Reuse detection
        String compromisedUserId = (String) redisTemplate.opsForValue().get(usedKey);
        if (compromisedUserId != null) {
            UUID userId = UUID.fromString(compromisedUserId);
            log.error("Refresh token reuse detected for user: {}", userId);
            revokeAllUserSessions(userId);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Validate token
        String userIdStr = (String) redisTemplate.opsForValue().get(validKey);
        if (userIdStr == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        UUID userId = UUID.fromString(userIdStr);

        // Rotate token
        redisTemplate.delete(validKey);
        redisTemplate.opsForValue().set(usedKey, userIdStr, refreshTokenExpiration, TimeUnit.SECONDS);
        redisTemplate.opsForSet().remove("auth:user:" + userId + ":sessions", hashedToken);

        return userId;
    }

    public void revokeAllUserSessions(UUID userId) {
        String userSessionsKey = "auth:user:" + userId + ":sessions";
        Set<Object> activeHashes = redisTemplate.opsForSet().members(userSessionsKey);

        if (activeHashes != null && !activeHashes.isEmpty()) {
            for (Object hash : activeHashes) {
                redisTemplate.delete("auth:refresh:valid:" + hash);
            }
        }
        redisTemplate.delete(userSessionsKey);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new AppException(ErrorCode.TOKEN_GENERATION_FAILED);
        }
    }
    public void revokeRefreshToken(String plainToken) {
        String hashedToken = hashToken(plainToken);
        String validKey = "auth:refresh:valid:" + hashedToken;

        String userIdStr = (String) redisTemplate.opsForValue().get(validKey);
        if (userIdStr == null) return;

        redisTemplate.delete(validKey);
        redisTemplate.opsForSet().remove("auth:user:" + userIdStr + ":sessions", hashedToken);
    }

}