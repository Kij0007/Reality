package com.reality.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Sign in to use Reality.");
        }
        return principal.id();
    }
}
