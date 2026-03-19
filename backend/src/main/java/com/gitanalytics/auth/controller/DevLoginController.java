package com.gitanalytics.auth.controller;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.shared.security.JwtService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevLoginController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Returns the demo user's JWT as JSON.
     * Called server-side by the Next.js /api/demo-login route, which sets
     * the cookie and redirects the browser — avoids proxy redirect issues.
     */
    @GetMapping("/demo-token")
    public ResponseEntity<ApiResponse<Map<String, String>>> demoToken() {
        User demo = userRepository.findByGithubId(999_999L)
                .orElseThrow(() -> new IllegalStateException("Demo user not found"));

        String token = jwtService.generateToken(demo.getId(), demo.getUsername());
        String refreshToken = jwtService.generateRefreshToken(demo.getId());
        log.info("Demo token issued for user '{}'", demo.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "userId", demo.getId().toString(),
                "username", demo.getUsername()
        )));
    }
}
