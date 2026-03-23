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
import com.jargoyle.entity.Role;
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

    private static final String DEV_USER_SUBJECT = "dev-user-001";
    private static final String DEV_USER_EMAIL = "dev@jargoyle.local";
    private static final String DEV_USER_NAME = "Dev User";

    private static final String DEV_ADMIN_SUBJECT = "dev-admin-001";
    private static final String DEV_ADMIN_EMAIL = "admin@jargoyle.local";
    private static final String DEV_ADMIN_NAME = "Dev Admin";

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
        var user = findOrCreateDevUser(DEV_USER_SUBJECT, DEV_USER_EMAIL, DEV_USER_NAME, Role.USER);
        setUpSecurityContext(request, user);
        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Same as /login but creates an admin user, useful for testing admin-only
     * endpoints and UI without needing to manually update the database.
     */
    @PostMapping("/login-admin")
    public ResponseEntity<UserDto> loginAdmin(HttpServletRequest request) {
        var user = findOrCreateDevUser(DEV_ADMIN_SUBJECT, DEV_ADMIN_EMAIL, DEV_ADMIN_NAME, Role.ADMIN);
        setUpSecurityContext(request, user);
        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Builds a fake OidcUser and OAuth2AuthenticationToken, then stores them
     * in both the SecurityContext and the HTTP session so that subsequent
     * requests in this session are treated as authenticated.
     */
    private void setUpSecurityContext(HttpServletRequest request, User user) {
        var token = OidcIdToken
            .withTokenValue("dev-token")
            .claim("sub", user.getOauthSubject())
            .claim("email", user.getEmail())
            .claim("name", user.getDisplayName())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(1)))
            .build();

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
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

    private User findOrCreateDevUser(String subject, String email, String name, Role role) {
        return userRepository
            .findByOauthProviderAndOauthSubject(DEV_PROVIDER, subject)
            .map(existing -> {
                // Ensure the role is up to date (e.g. if the user was created
                // before the role column existed).
                existing.setRole(role);
                existing.setEnabled(true);
                existing.setLastLoginAt(Instant.now());
                return userRepository.save(existing);
            })
            .orElseGet(() -> {
                var user = new User();
                user.setDisplayName(name);
                user.setEmail(email);
                user.setOauthProvider(DEV_PROVIDER);
                user.setOauthSubject(subject);
                user.setRole(role);
                user.setEnabled(true);
                user.setLastLoginAt(Instant.now());
                return userRepository.save(user);
            });
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getOauthProvider(), user.getRole().name(), user.isEnabled());
    }
}
