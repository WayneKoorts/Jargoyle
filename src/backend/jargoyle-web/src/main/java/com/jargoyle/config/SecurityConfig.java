package com.jargoyle.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.jargoyle.service.CustomOidcUserService;

@Configuration
// Enables @PreAuthorize and @PostAuthorize on controller/service methods.
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOidcUserService _customOidcUserService;
    private final Optional<OAuth2AuthorizationRequestResolver> _authorizationRequestResolver;
    private final String _oauthSuccessUrl;

    public SecurityConfig(
            CustomOidcUserService customOidcUserService,
            Optional<OAuth2AuthorizationRequestResolver> authorizationRequestResolver,
            @Value("${spring.oauth-success-url:/}") String oauthSuccessUrl) {
        _customOidcUserService = customOidcUserService;
        _authorizationRequestResolver = authorizationRequestResolver;
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
                    _authorizationRequestResolver.ifPresent(auth::authorizationRequestResolver)
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
            .build();
    }    
}
