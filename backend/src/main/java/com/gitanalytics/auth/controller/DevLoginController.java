package com.gitanalytics.auth.controller;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.shared.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevLoginController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @GetMapping("/demo-login")
    public void demoLogin(HttpServletResponse response) throws IOException {
        User demo = userRepository.findByGithubId(999_999L)
                .orElseThrow(() -> new IllegalStateException("Demo user not found"));

        String token = jwtService.generateToken(demo.getId(), demo.getUsername());

        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);

        log.info("Demo login issued for user '{}'", demo.getUsername());
        response.sendRedirect(frontendUrl + "/dashboard");
    }
}
