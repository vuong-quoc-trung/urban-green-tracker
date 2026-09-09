package com.urbangreen.urban_green_tracker.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.urbangreen.urban_green_tracker.exception.AppException;
import com.urbangreen.urban_green_tracker.exception.ErrorCode;
import com.urbangreen.urban_green_tracker.user.entity.User;
import com.urbangreen.urban_green_tracker.user.entity.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {
    private static final String SECRET = "29FfRFjQggJ7UoQumQGuv1Y3Rsy6b3cg60w+3l5csoxs21DatAtwkXzJm/VJWulRCugondVSNpBQBUOJLp7xEQ==";
    private static final String DIFFERENT_SECRET = "another-secret-key-that-is-long-enough-for-hmac-signing-tests-123456";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private StringRedisTemplate redisTemplate;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(redisTemplate);
        tokenService.SECRET_KEY = SECRET;
        tokenService.ACCESS_TOKEN_EXPIRATION = 900;
    }

    @Test
    void verifyAccessToken_acceptsValidAccessToken() {
        String token = tokenService.generateAccessToken(User.builder()
                .id(USER_ID)
                .role(Role.USER)
                .build());

        JWTClaimsSet claimsSet = tokenService.verifyAccessToken(token);

        assertThat(claimsSet.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claimsSet.getIssuer()).isEqualTo("urbangreentracker.com");
    }

    @Test
    void verifyAccessToken_rejectsNonsenseToken() {
        assertInvalidToken("this-is-not-a-jwt");
    }

    @Test
    void verifyAccessToken_rejectsAlgorithmSubstitution() throws JOSEException {
        String token = signToken(JWSAlgorithm.HS256, "urbangreentracker.com",
                Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)), "ACCESS", SECRET);

        assertInvalidToken(token);
    }

    @Test
    void verifyAccessToken_rejectsWrongSecret() throws JOSEException {
        String token = signToken(JWSAlgorithm.HS512, "urbangreentracker.com",
                Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)), "ACCESS", DIFFERENT_SECRET);

        assertInvalidToken(token);
    }

    @Test
    void verifyAccessToken_rejectsExpiredToken() throws JOSEException {
        String token = signToken(JWSAlgorithm.HS512, "urbangreentracker.com",
                Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)), "ACCESS", SECRET);

        assertInvalidToken(token);
    }

    @Test
    void verifyAccessToken_rejectsWrongIssuer() throws JOSEException {
        String token = signToken(JWSAlgorithm.HS512, "another-issuer.com",
                Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)), "ACCESS", SECRET);

        assertInvalidToken(token);
    }

    @Test
    void verifyAccessToken_rejectsNonAccessToken() throws JOSEException {
        String token = signToken(JWSAlgorithm.HS512, "urbangreentracker.com",
                Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)), "REFRESH", SECRET);

        assertInvalidToken(token);
    }

    @Test
    void verifyAccessToken_rejectsRevokedToken() throws JOSEException {
        String token = signToken(JWSAlgorithm.HS512, "urbangreentracker.com",
                Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)), "ACCESS", SECRET);

        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertInvalidToken(token);
    }

    private void assertInvalidToken(String token) {
        assertThatThrownBy(() -> tokenService.verifyAccessToken(token))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private String signToken(JWSAlgorithm algorithm, String issuer, Date expirationTime,
                             String tokenType, String secret) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(USER_ID.toString())
                .issueTime(new Date())
                .expirationTime(expirationTime)
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", tokenType)
                .build();

        JWSObject jwsObject = new JWSObject(new JWSHeader(algorithm), new Payload(claimsSet.toJSONObject()));
        jwsObject.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));

        return jwsObject.serialize();
    }
}
