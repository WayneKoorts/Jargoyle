package com.jargoyle.security;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import com.jargoyle.entity.User;
import com.jargoyle.repository.UserRepository;

@Component
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public AuthenticatedUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolves the OIDC principal to the local User entity.
     * Throws {@link UserNotFoundException} if the user doesn't exist locally.
     */
    public User resolve(OidcUser oidcUser, OAuth2AuthenticationToken authToken) {
        if (oidcUser == null || authToken == null) {
            throw new IllegalArgumentException("oidcUser and authToken must not be null");
        }

        var provider = authToken.getAuthorizedClientRegistrationId();
        var subject = oidcUser.getName();

        var localUser = userRepository.findByOauthProviderAndOauthSubject(provider, subject);
        if (localUser.isEmpty()) {
            throw new UserNotFoundException(provider, subject);
        }

        var user = localUser.get();

        return user;
    }
}
