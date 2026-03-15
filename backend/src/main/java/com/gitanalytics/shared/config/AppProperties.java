package com.gitanalytics.shared.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private GitHub github = new GitHub();
    private Encryption encryption = new Encryption();
    private Cors cors = new Cors();
    private Redis redis = new Redis();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs;
    }

    @Data
    public static class GitHub {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String webhookSecret;
        private String webhookUrl;
    }

    @Data
    public static class Encryption {
        private String key;
    }

    @Data
    public static class Cors {
        private String allowedOrigins;
    }

    @Data
    public static class Redis {
        private long dashboardCacheTtl;
        private long oauthStateTtl;
        private long rateLimitTtl;
        private long syncLockTtl;
        private long tokenRevokedTtl;
        private long streakCacheTtl;
    }
}
