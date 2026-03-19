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
import java.security.MessageDigest;
import java.util.Base64;
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
            if (claims.getExpiration().before(new Date())) return false;
            String hash = hashToken(token);
            try {
                return Boolean.FALSE.equals(redisTemplate.hasKey("ga:token:revoked:" + hash));
            } catch (Exception redisEx) {
                log.error("Redis unavailable during token validation, failing closed: {}", redisEx.getMessage());
                return false;
            }
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public void revokeToken(String token) {
        String hash = hashToken(token);
        redisTemplate.opsForValue().set(
            "ga:token:revoked:" + hash,
            "1",
            appProperties.getRedis().getTokenRevokedTtl(),
            TimeUnit.SECONDS
        );
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    private SecretKey getKey() {
        byte[] keyBytes = appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
}
