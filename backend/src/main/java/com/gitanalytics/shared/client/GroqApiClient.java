package com.gitanalytics.shared.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Free-tier Groq API client (llama3-8b-8192 — 14,400 req/day free, no card required).
 * Falls back gracefully to null if API key not configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroqApiClient {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama3-8b-8192";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.groq.api-key:}")
    private String apiKey;

    /**
     * Returns a 1-3 sentence AI insight or null if Groq is not configured / call fails.
     */
    public String complete(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) return null;
        try {
            Map<String, Object> body = Map.of(
                "model", MODEL,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.7,
                "max_tokens", 200
            );

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
            return root.path("choices").get(0).path("message").path("content").asText().strip();
        } catch (Exception e) {
            log.warn("Groq API call failed: {}", e.getMessage());
            return null;
        }
    }
}
