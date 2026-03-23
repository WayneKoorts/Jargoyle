package com.jargoyle.config;

import java.util.HashMap;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.jargoyle.service.CustomOidcUserService;

@Configuration
// Enables @PreAuthorize and @PostAuthorize on controller/service methods.
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOidcUserService _customOidcUserService;
    private final EnabledUserFilter _enabledUserFilter;
    private final ClientRegistrationRepository _clientRegistrationRepository;
    private final String _oauthSuccessUrl;

    public SecurityConfig(
            CustomOidcUserService customOidcUserService,
            EnabledUserFilter enabledUserFilter,
            ClientRegistrationRepository clientRegistrationRepository,
            @Value("${spring.oauth-success-url:/}") String oauthSuccessUrl) {
        _customOidcUserService = customOidcUserService;
        _enabledUserFilter = enabledUserFilter;
        _clientRegistrationRepository = clientRegistrationRepository;
        _oauthSuccessUrl = oauthSuccessUrl;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/index.html", "/error",
                    // SPA static assets (Vite outputs to /assets/)
                    "/assets/**", "/css/**", "/js/**",
                    // Swagger
                    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                ).permitAll()
                // Allow unauthenticated calls so the SPA gets a 401 JSON response
                // instead of being redirected to the OAuth login page.
                .requestMatchers("/api/auth/me").permitAll()
                // Dev-only login endpoint — no controller is registered outside the
                // "dev" profile, so this harmlessly matches nothing in production.
                .requestMatchers("/api/dev/**").permitAll()
                // Admin endpoints require the ADMIN role. This URL-based rule
                // provides defence-in-depth on top of any @PreAuthorize annotations
                // on individual controller methods.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(auth ->
                    auth.authorizationRequestResolver(createAuthorizationRequestResolver())
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(_customOidcUserService)
                )
                // After OAuth completes, redirect to the SPA.
                // In dev this points at the Vite dev server (localhost:5173);
                // in production it's just "/" since the backend serves the SPA.
                .defaultSuccessUrl(_oauthSuccessUrl, true)
            )
            // Return 401 for API requests instead of redirecting to the OAuth login page.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                )
            )
            // The SPA sends JSON via fetch, not HTML form submissions, so CSRF
            // protection for API paths is unnecessary.
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .addFilterAfter(_enabledUserFilter, AnonymousAuthenticationFilter.class)
            .build();
    }

    /**
     * Creates a resolver that conditionally adds {@code prompt=select_account}
     * to Google's OAuth2 authorisation request. The prompt parameter is only
     * added when the incoming request includes {@code ?prompt=select_account}
     * as a query parameter — this lets the frontend offer a "Switch account"
     * option that forces the Google account chooser, while the normal "Sign in
     * with Google" flow auto-selects the existing session.
     *
     * <p>Replaces the former {@code DevSecurityConfig} approach, which
     * unconditionally forced the account chooser in the dev profile only.
     */
    private OAuth2AuthorizationRequestResolver createAuthorizationRequestResolver() {
        var defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
            _clientRegistrationRepository, "/oauth2/authorization");

        // The DefaultOAuth2AuthorizationRequestResolver's setAuthorizationRequestCustomizer
        // doesn't expose the original HttpServletRequest, so we wrap the resolver interface
        // directly to inspect the request's query parameters before decorating the result.
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return addPromptIfRequested(request, defaultResolver.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return addPromptIfRequested(request, defaultResolver.resolve(request, clientRegistrationId));
            }

            private OAuth2AuthorizationRequest addPromptIfRequested(
                    HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
                if (authorizationRequest == null) {
                    return null;
                }
                if (!"select_account".equals(request.getParameter("prompt"))) {
                    return authorizationRequest;
                }
                // Copy existing additional parameters and add the Google-specific
                // prompt parameter that forces the account chooser to appear.
                var additionalParams = new HashMap<>(authorizationRequest.getAdditionalParameters());
                additionalParams.put("prompt", "select_account");
                return OAuth2AuthorizationRequest.from(authorizationRequest)
                    .additionalParameters(additionalParams)
                    .build();
            }
        };
    }
}
