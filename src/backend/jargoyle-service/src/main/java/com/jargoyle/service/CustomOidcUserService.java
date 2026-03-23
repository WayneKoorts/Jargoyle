package com.jargoyle.service;

import java.time.Instant;
import java.util.ArrayList;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.jargoyle.entity.User;
import com.jargoyle.repository.UserRepository;

@Service
public class CustomOidcUserService extends OidcUserService {
    private final UserRepository userRepository;

    CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        var providerName = userRequest.getClientRegistration().getRegistrationId();
        if (providerName.isBlank()) {
            throw new OAuth2AuthenticationException("Provider not specified.");
        }

        var loadedUser = super.loadUser(userRequest);
        var subjectName = loadedUser.getName(); // Returns the "sub" attribute, not the "name" attribute.
        if (subjectName.isBlank()) {
            throw new OAuth2AuthenticationException("Subject name not specified.");
        }

        // Find or create the local user, then inject their database role into
        // the Spring Security authority list so hasRole("ADMIN") works.
        var localUser = userRepository.findByOauthProviderAndOauthSubject(providerName, subjectName)
                .map(this::updateUserLoginTime)
                .orElseGet(() -> createNewUserFromLoadedData(loadedUser, providerName, subjectName));

        return wrapWithLocalAuthorities(loadedUser, localUser);
    }

    /**
     * Wraps the provider's OidcUser in a new DefaultOidcUser that carries the
     * local role as a GrantedAuthority. This is how the local database role
     * gets surfaced to Spring Security — the provider (e.g. Google) knows
     * nothing about our roles, so we add them here after the OIDC handshake.
     */
    private OidcUser wrapWithLocalAuthorities(OidcUser loadedUser, User localUser) {
        var authorities = new ArrayList<GrantedAuthority>(loadedUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + localUser.getRole().name()));

        // The two-arg constructor is safe when UserInfo is null (some providers
        // don't return it). The three-arg form adds UserInfo when available.
        if (loadedUser.getUserInfo() != null) {
            return new DefaultOidcUser(authorities, loadedUser.getIdToken(), loadedUser.getUserInfo());
        }
        return new DefaultOidcUser(authorities, loadedUser.getIdToken());
    }

    private User updateUserLoginTime(User user) {
        user.setLastLoginAt(Instant.now());
        try {
            return userRepository.save(user);
        } catch (Exception ex) {
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }

    private User createNewUserFromLoadedData(OidcUser loadedUser, String providerName, String subjectName) {
        var newUser = new User();
        var displayName = loadedUser.getAttribute("name");
        if (displayName == null) displayName = "Unknown";
        newUser.setDisplayName((String) displayName);
        newUser.setOauthProvider(providerName);
        newUser.setOauthSubject(subjectName);
        newUser.setLastLoginAt(Instant.now());
        newUser.setEnabled(false);

        var email = loadedUser.getAttribute("email");
        if (email == null) email = "notset";
        newUser.setEmail((String) email);

        // New users default to Role.USER (set in the entity default), so no
        // explicit setRole call is needed here. They also start disabled until
        // an admin explicitly enables them.

        try {
            return userRepository.save(newUser);
        } catch (Exception ex) {
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }
}
