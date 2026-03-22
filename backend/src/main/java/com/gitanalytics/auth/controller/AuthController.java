package com.gitanalytics.auth.controller;

import com.gitanalytics.auth.dto.UserProfileDto;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.auth.service.GitHubOAuthService;
import com.gitanalytics.shared.config.AppProperties;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import com.gitanalytics.shared.security.JwtService;
import com.gitanalytics.shared.util.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
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
    private final AppProperties appProperties;

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
        String accessToken = jwtService.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // Pass tokens to the Next.js callback route which sets the cookie on the
        // frontend domain. Cookies set here (backend domain) would be silently
        // dropped by the browser when it follows the redirect to Vercel.
        String frontendUrl = appProperties.getFrontendUrl().replaceAll("/+$", "");
        String redirectUrl = frontendUrl
            + "/api/auth/callback?token=" + accessToken
            + "&refresh=" + refreshToken;
        response.sendRedirect(redirectUrl);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = appProperties.isCookieSecure();
        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                .filter(c -> "jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .ifPresent(jwtService::revokeToken);
            Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .ifPresent(jwtService::revokeRefreshToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from("jwt", "").httpOnly(true).secure(secure)
                .path("/").sameSite("Lax").maxAge(Duration.ZERO).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from("refresh_token", "").httpOnly(true).secure(secure)
                .path("/api/v1/auth/refresh").sameSite("Lax").maxAge(Duration.ZERO).build().toString());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
        }
        UUID userId = jwtService.validateRefreshToken(refreshToken);
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid or expired refresh token"));
        }
        User user = userRepository.findById(userId)
            .orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("User not found"));
        }
        String newAccessToken = jwtService.generateToken(user.getId(), user.getUsername());
        boolean secure = appProperties.isCookieSecure();
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", newAccessToken)
            .httpOnly(true).secure(secure).path("/")
            .sameSite("Lax").maxAge(Duration.ofMinutes(15))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID userId = UUID.fromString(principal.getUsername());
        userRepository.deleteById(userId);
        boolean secure = appProperties.isCookieSecure();
        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .ifPresent(jwtService::revokeRefreshToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from("jwt", "").httpOnly(true).secure(secure)
                .path("/").sameSite("Lax").maxAge(Duration.ZERO).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from("refresh_token", "").httpOnly(true).secure(secure)
                .path("/api/v1/auth/refresh").sameSite("Lax").maxAge(Duration.ZERO).build().toString());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> me(@AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(ApiResponse.ok(UserProfileDto.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build()));
    }
}
