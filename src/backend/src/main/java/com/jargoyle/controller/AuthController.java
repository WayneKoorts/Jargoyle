package com.jargoyle.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jargoyle.dto.UserDto;
import com.jargoyle.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository _userRepository;

    public AuthController(UserRepository userRepository) {
        _userRepository = userRepository;
    }

    /**
     * Returns the currently authenticated user's public profile, or 401 if not logged in.
     * The provider name comes from the OAuth2AuthenticationToken rather than being hardcoded,
     * so this works with any configured OAuth provider.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(
            @AuthenticationPrincipal OidcUser oidcUser,
            OAuth2AuthenticationToken authToken) {

        if (oidcUser == null || authToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var provider = authToken.getAuthorizedClientRegistrationId();
        var subject = oidcUser.getName();

        var localUser = _userRepository.findByOauthProviderAndOauthSubject(provider, subject);
        if (localUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var user = localUser.get();
        var dto = new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getOauthProvider());
        return ResponseEntity.ok(dto);
    }

    /**
     * Logs out the current user by invalidating the HTTP session and clearing the security context.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
