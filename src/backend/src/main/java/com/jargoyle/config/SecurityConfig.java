package com.jargoyle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.jargoyle.service.CustomOidcUserService;

@Configuration
public class SecurityConfig {

    private CustomOidcUserService _customOidcUserService;
    private ClientRegistrationRepository _clientRegistrationRepository;
    
    public SecurityConfig(
        CustomOidcUserService customOidcUserService,
        ClientRegistrationRepository clientRegistrationRepository) {
        _customOidcUserService = customOidcUserService;
        _clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(auth -> auth
                    .authorizationRequestResolver(
                        authorizationRequestResolver(_clientRegistrationRepository)
                    )
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(_customOidcUserService)
                )
            )
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .build();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
        ClientRegistrationRepository clientRegistrationRepository) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(customizer ->
            customizer.additionalParameters(params ->
                params.put("prompt", "select_account")
            )
        );

        return resolver;
    }
}
