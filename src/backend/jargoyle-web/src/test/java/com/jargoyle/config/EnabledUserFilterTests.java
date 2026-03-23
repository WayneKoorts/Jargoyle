package com.jargoyle.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.jargoyle.entity.User;
import com.jargoyle.service.security.AuthenticatedUserResolver;

import jakarta.servlet.ServletException;

/**
 * Tests the filter that blocks disabled accounts from protected API routes.
 */
class EnabledUserFilterTests {

    private final AuthenticatedUserResolver authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
    private final EnabledUserFilter enabledUserFilter = new EnabledUserFilter(authenticatedUserResolver);

    /**
     * Ensures disabled accounts receive a 403 before the request reaches the
     * rest of the application.
     */
    @Test
    void blocksDisabledUserOnProtectedApi() throws ServletException, IOException {
        var authToken = setAuthentication();
        var disabledUser = new User();
        disabledUser.setEnabled(false);

        when(authenticatedUserResolver.resolve((OidcUser) authToken.getPrincipal(), authToken)).thenReturn(disabledUser);

        var request = new MockHttpServletRequest("GET", "/api/documents");
        var response = new MockHttpServletResponse();
        var chainCalled = new AtomicBoolean(false);

        enabledUserFilter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).isEqualTo("Your account has not yet been enabled.");
    }

    /**
     * Keeps /api/auth/me accessible so the SPA can show the waiting-for-approval
     * message instead of a generic authorisation failure.
     */
    @Test
    void allowsDisabledUserToCallAuthEndpoint() throws ServletException, IOException {
        var authToken = setAuthentication();
        var disabledUser = new User();
        disabledUser.setEnabled(false);

        when(authenticatedUserResolver.resolve((OidcUser) authToken.getPrincipal(), authToken)).thenReturn(disabledUser);

        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        var response = new MockHttpServletResponse();
        var chainCalled = new AtomicBoolean(false);

        enabledUserFilter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static OAuth2AuthenticationToken setAuthentication() {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var idToken = OidcIdToken.withTokenValue("token")
            .claim("sub", "subject-123")
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(3600))
            .build();
        var oidcUser = new DefaultOidcUser(authorities, idToken);
        var authentication = new OAuth2AuthenticationToken(oidcUser, authorities, "google");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }
}
