package com.jargoyle.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.jargoyle.entity.User;
import com.jargoyle.service.security.AuthenticatedUserResolver;
                                                                                                                                                  
/**
 * Resolves controller method parameters annotated with {@link CurrentUser}
 * to the local {@link User} entity for the currently authenticated session.                                                                    
 *
 * <p>Extracts the OIDC principal from the Spring Security context and
 * delegates to {@link AuthenticatedUserResolver} to look up the
 * corresponding database record.</p>
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthenticatedUserResolver authenticatedUserResolver;

    public CurrentUserArgumentResolver(AuthenticatedUserResolver authenticatedUserResolver) {
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
               && parameter.getParameterType() == User.class;
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
        var securityContext = SecurityContextHolder.getContext();
        var auth = securityContext.getAuthentication();
        var token = (OAuth2AuthenticationToken) auth;
        var oidcUser = (OidcUser) token.getPrincipal();

        return authenticatedUserResolver.resolve(oidcUser, token);
    }

}
