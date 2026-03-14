package com.jargoyle.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jargoyle.dto.UserDto;
import com.jargoyle.entity.User;
import com.jargoyle.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Dev-only controller that creates a test session without going through OAuth.
 * Only active when the "dev" Spring profile is enabled — this bean does not
 * exist in production, so the endpoint simply returns 404.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
public class DevAuthController {

    private static final String DEV_PROVIDER = "dev";
    private static final String DEV_SUBJECT = "dev-user-001";
    private static final String DEV_EMAIL = "dev@jargoyle.local";
    private static final String DEV_NAME = "Dev User";

    private final UserRepository userRepository;

    public DevAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates (or retrieves) a test user and establishes an authenticated
     * session, bypassing the OAuth flow entirely.
     *
     * Usage from Postman / Insomnia etc.:  POST http://localhost:8080/api/dev/login
     * The response sets a JSESSIONID cookie — include it in subsequent requests.
     */
    @PostMapping("/login")
    public ResponseEntity<UserDto> login(HttpServletRequest request) {
        // Step 1: Ensure a local User entity exists in the database.
        var user = findOrCreateDevUser();

        // Step 2: Build the fake OIDC/OAuth objects and set them in the
        // security context so the rest of the app sees a "logged-in" user.
        setUpSecurityContext(request);

        var dto = new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getOauthProvider());
        return ResponseEntity.ok(dto);
    }

    /**
     * Builds a fake OidcUser and OAuth2AuthenticationToken, then stores them
     * in both the SecurityContext and the HTTP session so that subsequent
     * requests in this session are treated as authenticated.
     */
    private void setUpSecurityContext(HttpServletRequest request) {
        var token = OidcIdToken
            .withTokenValue("dev-token")
            .claim("sub", DEV_SUBJECT)
            .claim("email", DEV_EMAIL)
            .claim("name", DEV_NAME)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(1)))
            .build();

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var defaultUser = new DefaultOidcUser(authorities, token);
        var authToken = new OAuth2AuthenticationToken(defaultUser, authorities, DEV_PROVIDER);

        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Persist the security context in the HTTP session so subsequent
        // requests with the same JSESSIONID cookie are treated as authenticated.
        request.getSession(true).setAttribute(
            "SPRING_SECURITY_CONTEXT",
            SecurityContextHolder.getContext()
        );
    }

    private User findOrCreateDevUser() {
        return userRepository
            .findByOauthProviderAndOauthSubject(DEV_PROVIDER, DEV_SUBJECT)
            .orElseGet(() -> {
                var user = new User();
                user.setDisplayName(DEV_NAME);
                user.setEmail(DEV_EMAIL);
                user.setOauthProvider(DEV_PROVIDER);
                user.setOauthSubject(DEV_SUBJECT);
                user.setLastLoginAt(Instant.now());
                return userRepository.save(user);
            });
    }
}
