package com.gitanalytics.auth.controller;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.shared.security.JwtService;
import com.gitanalytics.shared.util.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Dev-only endpoints for easy local testing without GitHub OAuth.
 * NOT available in production (requires "dev" Spring profile).
 */
@Slf4j
@RestController
@RequestMapping("/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevLoginController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Issues a JWT cookie for the seeded demo user and redirects to the dashboard.
     * Visit http://localhost:8080/api/v1/dev/demo-login in your browser.
     */
    @GetMapping("/demo-login")
    public void demoLogin(HttpServletResponse response) throws IOException {
        User demo = userRepository.findByGithubId(999_999L)
            .orElseThrow(() -> new IllegalStateException(
                "Demo user not found. Make sure SPRING_PROFILES_ACTIVE=dev to run SeedDataRunner."));

        String token = jwtService.generateToken(demo.getId(), demo.getUsername());

        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);

        log.info("Demo login issued for user '{}' ({})", demo.getUsername(), demo.getId());
        response.sendRedirect("http://localhost:3000/dashboard");
    }

    /**
     * Returns the JWT token as JSON (for API clients / Postman testing).
     */
    @GetMapping("/demo-token")
    public ResponseEntity<ApiResponse<Map<String, String>>> demoToken() {
        User demo = userRepository.findByGithubId(999_999L)
            .orElseThrow(() -> new IllegalStateException("Demo user not found. Run with SPRING_PROFILES_ACTIVE=dev."));

        String token = jwtService.generateToken(demo.getId(), demo.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "token", token,
            "userId", demo.getId().toString(),
            "username", demo.getUsername()
        )));
    }
}
