package com.gitanalytics.auth.controller;

import com.gitanalytics.auth.dto.UserProfileDto;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.auth.service.GitHubOAuthService;
import com.gitanalytics.shared.security.JwtService;
import com.gitanalytics.shared.util.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GitHubOAuthService gitHubOAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @GetMapping("/github")
    public void initiateOAuth(HttpServletResponse response) throws IOException {
        String authUrl = gitHubOAuthService.buildAuthorizationUrl();
        response.sendRedirect(authUrl);
    }

    @GetMapping("/github/callback")
    public void handleCallback(@RequestParam String code,
                                @RequestParam String state,
                                HttpServletResponse response) throws IOException {
        User user = gitHubOAuthService.handleCallback(code, state);
        String token = jwtService.generateToken(user.getId(), user.getUsername());

        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(cookie);

        // Redirect to frontend dashboard
        response.sendRedirect(System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:3000") + "/dashboard");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request,
                                                     HttpServletResponse response) {
        // Revoke JWT
        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                .filter(c -> "jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .ifPresent(jwtService::revokeToken);
        }

        // Clear cookie
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> me(@AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(ApiResponse.ok(UserProfileDto.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build()));
    }
}
