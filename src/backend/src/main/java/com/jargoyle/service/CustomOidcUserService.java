package com.jargoyle.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.jargoyle.entity.User;
import com.jargoyle.repository.UserRepository;

@Service
public class CustomOidcUserService extends OidcUserService {
    private UserRepository _userRepository;

    CustomOidcUserService(UserRepository userRepository) {
        this._userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
    var providerName = userRequest.getClientRegistration().getRegistrationId();
        if (providerName.isEmpty() || providerName.isBlank()) {
            throw new OAuth2AuthenticationException("Provider not specified.");
        }
        
        var loadedUser = super.loadUser(userRequest);        
        var subjectName = loadedUser.getName(); // Returns the "sub" attribute, not the "name" attribute.
        if (subjectName.isEmpty() || subjectName.isBlank()) {
            throw new OAuth2AuthenticationException("Subject name not specified.");
        }

        var localUser = _userRepository.findByOauthProviderAndOauthSubject(providerName, subjectName);
        if (localUser.isPresent()) {
            updateUserLoginTime(localUser);
            
        } else {
            createNewUserFromLoadedData(loadedUser, providerName, subjectName);
        }

        return loadedUser;
    }

    private void updateUserLoginTime(Optional<User> localUser) {
        // Exists already, just update their last login time.
        localUser.get().setLastLoginAt(Instant.now());

        try {
            _userRepository.save(localUser.get());
        } catch (Exception ex) {
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }

    private void createNewUserFromLoadedData(OidcUser loadedUser, String providerName, String subjectName) {
        // Doesn't exist, create.
        var newUser = new User();
        var displayName = loadedUser.getAttribute("name");
        if (displayName == null) displayName = "Unknown";
        newUser.setDisplayName((String) displayName);
        newUser.setOauthProvider(providerName);
        newUser.setOauthSubject(subjectName);
        newUser.setLastLoginAt(Instant.now());

        var email = loadedUser.getAttribute("email");
        if (email == null) email = "notset";
        newUser.setEmail((String) email);

        try {
            _userRepository.save(newUser);
        } catch (Exception ex) {
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }
}
