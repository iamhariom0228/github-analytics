package com.gitanalytics.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartupValidator {

    private final AppProperties appProperties;

    private static final String DEFAULT_JWT = "changeme-use-256-bit-secret-in-production-env-var";
    private static final String DEFAULT_ENC = "changeme-32-byte-key-for-aes-gcm!";

    @PostConstruct
    public void validate() {
        String jwt = appProperties.getJwt().getSecret();
        if (jwt == null || jwt.equals(DEFAULT_JWT) || jwt.length() < 32)
            throw new IllegalStateException("JWT_SECRET must be set to a secure value (32+ chars)");

        String enc = appProperties.getEncryption().getKey();
        if (enc == null || enc.equals(DEFAULT_ENC))
            throw new IllegalStateException("ENCRYPTION_KEY must be set to a secure 32-char value");
    }
}
