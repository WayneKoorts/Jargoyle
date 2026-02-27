package com.jargoyle.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.jargoyle.service.CustomOidcUserService;

@Configuration
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
                .requestMatchers("/", "/error", "/css/**", "/js/**").permitAll()
                // Allow unauthenticated calls so the SPA gets a 401 JSON response
                // instead of being redirected to the OAuth login page.
                .requestMatchers("/api/auth/me").permitAll()
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
