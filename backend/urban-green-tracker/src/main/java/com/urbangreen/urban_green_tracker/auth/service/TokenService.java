package com.urbangreen.urban_green_tracker.auth.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.urbangreen.urban_green_tracker.user.entity.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.urbangreen.urban_green_tracker.exception.AppException;
import com.urbangreen.urban_green_tracker.exception.ErrorCode;  

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TokenService {
    private static final JWSAlgorithm ACCESS_TOKEN_ALGORITHM = JWSAlgorithm.HS512;
    private static final String ISSUER = "urbangreentracker.com";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:access:";

    StringRedisTemplate redisTemplate;

    @NonFinal
    @Value("${jwt.secret}")
    protected String SECRET_KEY;

    @NonFinal
    @Value("${jwt.access-token-expiration}")
    protected long ACCESS_TOKEN_EXPIRATION;

    public String generateAccessToken(User user) {
        JWSHeader header = new JWSHeader(ACCESS_TOKEN_ALGORITHM);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(ISSUER) 
                .subject(user.getId().toString())
                .issueTime(new Date()) 
                .expirationTime(new Date(Instant.now().plus(ACCESS_TOKEN_EXPIRATION, ChronoUnit.SECONDS).toEpochMilli())) 
                .jwtID(UUID.randomUUID().toString()) 
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE) 
                .claim("scope", "ROLE_" + user.getRole().name()) 
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {

            jwsObject.sign(new MACSigner(SECRET_KEY.getBytes(StandardCharsets.UTF_8)));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot generate access token for user ID: {}", user.getId(), e);
            throw new AppException(ErrorCode.TOKEN_GENERATION_FAILED, "Cannot generate access token for user ID: " + user.getId());
        }
    }

    public JWTClaimsSet verifyAccessToken(String token) {
        String rawToken = requireReadableToken(token);
        SignedJWT signedJWT = parseSignedJWT(rawToken);

        verifyAlgorithm(signedJWT);
        verifySignature(signedJWT);

        JWTClaimsSet claimsSet = getClaimsSet(signedJWT);
        verifyExpiration(claimsSet);
        verifyIssuer(claimsSet);
        verifyAccessTokenType(claimsSet);
        verifyRequiredTokenIdentity(claimsSet);
        verifyTokenIsNotRevoked(claimsSet.getJWTID());

        return claimsSet;
    }

    private String requireReadableToken(String token) {
        if (token == null || token.isBlank()) {
            throwInvalidToken();
        }

        return token.trim();
    }

    private SignedJWT parseSignedJWT(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            throwInvalidToken();
            return null;
        }
    }

    private void verifyAlgorithm(SignedJWT signedJWT) {
        JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();

        if (!ACCESS_TOKEN_ALGORITHM.equals(algorithm)) {
            throwInvalidToken();
        }
    }

    private void verifySignature(SignedJWT signedJWT) {
        try {
            boolean verified = signedJWT.verify(new MACVerifier(SECRET_KEY.getBytes(StandardCharsets.UTF_8)));

            if (!verified) {
                throwInvalidToken();
            }
        } catch (JOSEException e) {
            throwInvalidToken();
        }
    }

    private JWTClaimsSet getClaimsSet(SignedJWT signedJWT) {
        try {
            return signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throwInvalidToken();
            return null;
        }
    }

    private void verifyExpiration(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();

        if (expirationTime == null || !expirationTime.after(new Date())) {
            throwInvalidToken();
        }
    }

    private void verifyIssuer(JWTClaimsSet claimsSet) {
        if (!ISSUER.equals(claimsSet.getIssuer())) {
            throwInvalidToken();
        }
    }

    private void verifyAccessTokenType(JWTClaimsSet claimsSet) {
        try {
            if (!ACCESS_TOKEN_TYPE.equals(claimsSet.getStringClaim(TOKEN_TYPE_CLAIM))) {
                throwInvalidToken();
            }
        } catch (ParseException e) {
            throwInvalidToken();
        }
    }

    private void verifyRequiredTokenIdentity(JWTClaimsSet claimsSet) {
        if (claimsSet.getSubject() == null || claimsSet.getSubject().isBlank()
                || claimsSet.getJWTID() == null || claimsSet.getJWTID().isBlank()) {
            throwInvalidToken();
        }
    }

    private void verifyTokenIsNotRevoked(String jwtId) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(getAccessTokenBlacklistKey(jwtId)))) {
            throwInvalidToken();
        }
    }

    private String getAccessTokenBlacklistKey(String jwtId) {
        return ACCESS_TOKEN_BLACKLIST_PREFIX + jwtId;
    }

    private void throwInvalidToken() {
        throw new AppException(ErrorCode.INVALID_TOKEN);
    }



    public void blacklistAccessToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();

            if (jti == null || expiration == null) return;

            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(
                        "auth:token:blacklist:access:" + jti,
                        "revoked",
                        ttlSeconds,
                        TimeUnit.SECONDS
                );
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist access token: {}", e.getMessage());
        }
    }
}
