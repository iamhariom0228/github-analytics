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
        props.setJwt(jwt);

        AppProperties.Redis redis = new AppProperties.Redis();
        redis.setTokenRevokedTtl(3600L);
        props.setRedis(redis);

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
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForRevokedToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "alice");
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForGarbage() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void revokeToken_storesHashInRedis() {
        String token = jwtService.generateToken(UUID.randomUUID(), "alice");
        jwtService.revokeToken(token);
        verify(valueOperations).set(
            startsWith("ga:token:revoked:"),
            eq("1"),
            eq(3600L),
            eq(TimeUnit.SECONDS)
        );
    }
}
