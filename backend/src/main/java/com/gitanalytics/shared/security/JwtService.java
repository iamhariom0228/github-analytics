package com.gitanalytics.shared.security;

import com.gitanalytics.shared.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    public String generateToken(UUID userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(new Date(now))
            .expiration(new Date(now + appProperties.getJwt().getExpirationMs()))
            .signWith(getKey())
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String generateRefreshToken(UUID userId) {
        String token = java.util.UUID.randomUUID().toString() +
                       java.util.UUID.randomUUID().toString(); // 64-char opaque token
        redisTemplate.opsForValue().set(
            "ga:refresh:" + token,
            userId.toString(),
            appProperties.getJwt().getRefreshTokenTtlSeconds(),
            TimeUnit.SECONDS
        );
        return token;
    }

    public UUID validateRefreshToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Object val = redisTemplate.opsForValue().get("ga:refresh:" + token);
            if (val == null) return null;
            return UUID.fromString(val.toString());
        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return null;
        }
    }

    public void revokeRefreshToken(String token) {
        if (token == null || token.isBlank()) return;
        try {
            redisTemplate.delete("ga:refresh:" + token);
        } catch (Exception e) {
            log.warn("Failed to revoke refresh token: {}", e.getMessage());
        }
    }

    private SecretKey getKey() {
        byte[] keyBytes = appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
