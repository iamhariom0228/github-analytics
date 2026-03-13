package com.gitanalytics.auth.service;

import com.gitanalytics.auth.dto.GitHubUserDto;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.entity.UserPreferences;
import com.gitanalytics.auth.repository.UserPreferencesRepository;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.shared.config.AppProperties;
import com.gitanalytics.shared.exception.UnauthorizedException;
import com.gitanalytics.shared.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubOAuthService {

    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";

    private final AppProperties appProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final EncryptionUtil encryptionUtil;
    private final WebClient.Builder webClientBuilder;

    public String buildAuthorizationUrl() {
        String state = UUID.randomUUID().toString();
        String stateKey = "ga:oauth:state:" + state;
        redisTemplate.opsForValue().set(stateKey, "valid",
            appProperties.getRedis().getOauthStateTtl(), TimeUnit.SECONDS);

        return "https://github.com/login/oauth/authorize" +
            "?client_id=" + appProperties.getGithub().getClientId() +
            "&scope=repo,read:org,user:email" +
            "&state=" + state;
    }

    @Transactional
    public User handleCallback(String code, String state) {
        validateState(state);

        String accessToken = exchangeCodeForToken(code);
        GitHubUserDto githubUser = fetchGitHubUser(accessToken);

        return upsertUser(githubUser, accessToken);
    }

    private void validateState(String state) {
        String stateKey = "ga:oauth:state:" + state;
        Object value = redisTemplate.opsForValue().get(stateKey);
        if (value == null) {
            throw new UnauthorizedException("Invalid or expired OAuth state");
        }
        redisTemplate.delete(stateKey);
    }

    @SuppressWarnings("unchecked")
    private String exchangeCodeForToken(String code) {
        Map<String, String> response = webClientBuilder.build()
            .post()
            .uri(GITHUB_TOKEN_URL)
            .header("Accept", "application/json")
            .bodyValue(Map.of(
                "client_id", appProperties.getGithub().getClientId(),
                "client_secret", appProperties.getGithub().getClientSecret(),
                "code", code,
                "redirect_uri", appProperties.getGithub().getRedirectUri()
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response == null || !response.containsKey("access_token")) {
            throw new UnauthorizedException("Failed to obtain GitHub access token");
        }
        return response.get("access_token");
    }

    private GitHubUserDto fetchGitHubUser(String accessToken) {
        return webClientBuilder.build()
            .get()
            .uri(GITHUB_USER_URL)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .bodyToMono(GitHubUserDto.class)
            .block();
    }

    private User upsertUser(GitHubUserDto githubUser, String accessToken) {
        String encryptedToken = encryptionUtil.encrypt(accessToken);

        User user = userRepository.findByGithubId(githubUser.getId())
            .map(existing -> {
                existing.setUsername(githubUser.getLogin());
                existing.setEmail(githubUser.getEmail());
                existing.setAvatarUrl(githubUser.getAvatarUrl());
                existing.setAccessTokenEncrypted(encryptedToken);
                return existing;
            })
            .orElseGet(() -> User.builder()
                .githubId(githubUser.getId())
                .username(githubUser.getLogin())
                .email(githubUser.getEmail())
                .avatarUrl(githubUser.getAvatarUrl())
                .accessTokenEncrypted(encryptedToken)
                .build());

        User savedUser = userRepository.save(user);

        // Create default preferences if new user
        userPreferencesRepository.findByUserId(savedUser.getId())
            .orElseGet(() -> userPreferencesRepository.save(
                UserPreferences.builder().user(savedUser).build()
            ));

        return savedUser;
    }

    public String decryptAccessToken(User user) {
        return encryptionUtil.decrypt(user.getAccessTokenEncrypted());
    }
}
