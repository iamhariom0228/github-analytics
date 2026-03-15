package com.gitanalytics.shared.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Free-tier Groq API client (llama3-8b-8192 — 14,400 req/day free, no card required).
 * Results are Redis-cached by content hash (TTL 6h) to conserve the daily quota.
 * Falls back gracefully to null if API key not configured or call fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroqApiClient {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";
    private static final long CACHE_TTL_HOURS = 6;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.groq.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Returns a completion string or null if Groq is not configured / call fails.
     * Results are cached in Redis by a hash of the prompt to avoid duplicate calls.
     */
    public String complete(String systemPrompt, String userMessage) {
        if (!isConfigured()) return null;

        String cacheKey = "ga:groq:" + hash(systemPrompt + "|" + userMessage);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String s && !s.isBlank()) return s;

        try {
            var body = new java.util.LinkedHashMap<String, Object>();
            body.put("model", MODEL);
            body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ));
            body.put("temperature", 0.7);
            body.put("max_tokens", 250);

            String response = webClientBuilder.build()
                .post()
                .uri(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null) return null;
            JsonNode root = objectMapper.readTree(response);
            String result = root.path("choices").get(0).path("message").path("content").asText().strip();
            if (!result.isBlank()) {
                redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL_HOURS, TimeUnit.HOURS);
            }
            return result.isBlank() ? null : result;
        } catch (Exception e) {
            log.warn("Groq API call failed: {}", e.getMessage());
            return null;
        }
    }

    private String hash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
