package com.gitanalytics.shared.seed;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.entity.UserPreferences;
import com.gitanalytics.auth.repository.UserPreferencesRepository;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.shared.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on every startup (all profiles) to ensure the demo user exists.
 * The dev-profile SeedDataRunner adds fake repo/commit/PR data on top of this.
 */
@Slf4j
@Component
@Order(1) // run before SeedDataRunner (order 2 by default)
@RequiredArgsConstructor
public class DemoUserInitializer implements ApplicationRunner {

    static final long DEMO_GITHUB_ID = 999_999L;

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final EncryptionUtil encryptionUtil;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByGithubId(DEMO_GITHUB_ID).isPresent()) {
            return; // already exists
        }

        User demo = userRepository.save(User.builder()
                .githubId(DEMO_GITHUB_ID)
                .username("demo-user")
                .email("demo@example.com")
                .avatarUrl("https://avatars.githubusercontent.com/u/583231")
                .accessTokenEncrypted(encryptionUtil.encrypt("demo-token-not-real"))
                .build());

        userPreferencesRepository.save(UserPreferences.builder()
                .user(demo)
                .build());

        log.info("Demo user created (id={})", demo.getId());
    }
}
