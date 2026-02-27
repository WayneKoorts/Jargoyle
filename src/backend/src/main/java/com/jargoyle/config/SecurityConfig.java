package com.jargoyle.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.jargoyle.service.CustomOidcUserService;

@Configuration
public class SecurityConfig {

    private CustomOidcUserService _customOidcUserService;
    private Optional<OAuth2AuthorizationRequestResolver> _authorizationRequestResolver;
    
    
    public SecurityConfig(
        CustomOidcUserService customOidcUserService,
        Optional<OAuth2AuthorizationRequestResolver> authorizationRequestResolver) {
        _customOidcUserService = customOidcUserService;
        _authorizationRequestResolver = authorizationRequestResolver;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(auth -> 
                    _authorizationRequestResolver.ifPresent(auth::authorizationRequestResolver)
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(_customOidcUserService)
                )
            )
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .build();
    }    
}
