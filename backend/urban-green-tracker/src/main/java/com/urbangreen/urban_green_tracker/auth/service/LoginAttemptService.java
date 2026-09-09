package com.urbangreen.urban_green_tracker.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {


    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS_PER_ACCOUNT = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 10;
    private static final long LOCK_DURATION_MINUTES = 15;

    public boolean isBlocked(String ip, String identifier) {
        String ipAttemptsStr = redisTemplate.opsForValue().get(getIpKey(ip));
        String accountAttemptsStr = redisTemplate.opsForValue().get(getAccountKey(identifier));

        int ipAttempts = ipAttemptsStr != null ? Integer.parseInt(ipAttemptsStr) : 0;
        int accountAttempts = accountAttemptsStr != null ? Integer.parseInt(accountAttemptsStr) : 0;

        return ipAttempts >= MAX_ATTEMPTS_PER_IP || accountAttempts >= MAX_ATTEMPTS_PER_ACCOUNT;
    }

    public void loginFailed(String ip, String identifier) {
        incrementAttempt(getIpKey(ip));
        incrementAttempt(getAccountKey(identifier));
        log.warn("Log a failed login attempt - IP: {}, Account: {}", ip, identifier);
    }

    public void loginSucceeded(String ip, String identifier) {
        redisTemplate.delete(getIpKey(ip));
        redisTemplate.delete(getAccountKey(identifier));
    }

    private void incrementAttempt(String key) {
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(LOCK_DURATION_MINUTES));
        }
    }

    private String getIpKey(String ip) {
        return "auth:login:ip:" + ip;
    }

    private String getAccountKey(String identifier) {
        return "auth:login:account:" + identifier;
    }
}