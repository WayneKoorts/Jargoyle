package com.jargoyle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

/**
 * Dev-only security overrides. Only loaded when the "dev" profile is active.
 *
 * <p>Provides beans that improve the local development experience but aren't
 * appropriate for production (e.g. forcing the OAuth account chooser).
 * SecurityConfig picks these up via Optional injection, so the app works
 * fine without them when this profile isn't active.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {
    private ClientRegistrationRepository _clientRegistrationRepository;

    public DevSecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        _clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Customises the OAuth2 authorisation request so that Google always shows
     * the account chooser (prompt=select_account). Without this, Google skips
     * the chooser if the user only has one active session, which makes it
     * difficult to test with different accounts during development.
     */
    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
            _clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(customizer ->
            customizer.additionalParameters(params ->
                params.put("prompt", "select_account")
            )
        );

        return resolver;
    }
}
