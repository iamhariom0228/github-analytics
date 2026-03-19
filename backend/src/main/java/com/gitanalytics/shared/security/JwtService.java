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
import java.security.MessageDigest;

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
            if (claims.getExpiration().before(new Date())) return false;
            try {
                String hash = sha256hex(token);
                if (Boolean.TRUE.equals(redisTemplate.hasKey("ga:token:revoked:" + hash))) return false;
            } catch (Exception e) {
                log.warn("Redis revocation check failed (fail-closed): {}", e.getMessage());
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public void revokeToken(String token) {
        try {
            Claims claims = parseToken(token);
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMs > 0) {
                String hash = sha256hex(token);
                redisTemplate.opsForValue().set(
                    "ga:token:revoked:" + hash, "1", ttlMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to revoke access token: {}", e.getMessage());
        }
    }

    private String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
