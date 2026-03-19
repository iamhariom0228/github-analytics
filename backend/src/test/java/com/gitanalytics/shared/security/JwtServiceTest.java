package com.gitanalytics.shared.security;

import com.gitanalytics.shared.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        AppProperties.Jwt jwt = new AppProperties.Jwt();
        jwt.setSecret("test-jwt-secret-that-is-long-enough-for-hs256-algorithm-at-least-32");
        jwt.setExpirationMs(3_600_000L);
        jwt.setRefreshTokenTtlSeconds(2_592_000L);
        props.setJwt(jwt);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        jwtService = new JwtService(props, redisTemplate);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "alice");
        assertThat(token).isNotBlank().contains(".");
    }

    @Test
    void parseToken_extractsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "alice");
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void isTokenValid_returnsTrueForFreshToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "alice");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForGarbage() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void generateRefreshToken_storesInRedis() {
        UUID userId = UUID.randomUUID();
        String refreshToken = jwtService.generateRefreshToken(userId);
        assertThat(refreshToken).isNotBlank();
        verify(valueOperations).set(
            startsWith("ga:refresh:"),
            eq(userId.toString()),
            eq(2_592_000L),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void validateRefreshToken_returnsUserIdWhenPresent() {
        UUID userId = UUID.randomUUID();
        String token = "some-opaque-token";
        when(valueOperations.get("ga:refresh:" + token)).thenReturn(userId.toString());
        assertThat(jwtService.validateRefreshToken(token)).isEqualTo(userId);
    }

    @Test
    void validateRefreshToken_returnsNullWhenMissing() {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertThat(jwtService.validateRefreshToken("nonexistent")).isNull();
    }

    @Test
    void revokeRefreshToken_deletesFromRedis() {
        String token = "some-opaque-token";
        jwtService.revokeRefreshToken(token);
        verify(redisTemplate).delete("ga:refresh:" + token);
    }
}
