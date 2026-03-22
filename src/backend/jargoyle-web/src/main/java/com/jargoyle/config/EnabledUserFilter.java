package com.jargoyle.config;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jargoyle.service.security.AuthenticatedUserResolver;
import com.jargoyle.service.security.UserNotFoundException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Blocks disabled users from protected API routes while still allowing
 * /api/auth/** so the SPA can show a clear "awaiting approval" message and
 * let the user log out cleanly.
 */
@Component
public class EnabledUserFilter extends OncePerRequestFilter {

    private static final String DISABLED_MESSAGE = "Your account has not yet been enabled.";

    private final AuthenticatedUserResolver authenticatedUserResolver;

    public EnabledUserFilter(AuthenticatedUserResolver authenticatedUserResolver) {
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.startsWith("/api/")
            || path.startsWith("/api/auth/")
            || path.startsWith("/api/dev/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken authToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!(authToken.getPrincipal() instanceof OidcUser oidcUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Re-resolving the local user on each request means an admin's
            // enable/disable toggle applies to an existing session immediately.
            var user = authenticatedUserResolver.resolve(oidcUser, authToken);
            if (!user.isEnabled()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, DISABLED_MESSAGE);
                return;
            }
        } catch (UserNotFoundException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
