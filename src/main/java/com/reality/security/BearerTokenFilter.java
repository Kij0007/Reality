package com.reality.security;

import java.io.IOException;
import java.util.List;
import com.reality.services.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Added only to Spring Security's chain, never registered as a second servlet filter.
public class BearerTokenFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityErrorWriter errors;
    public BearerTokenFilter(AuthService auth, SecurityErrorWriter errors) {
        this.auth = auth;
        this.errors = errors;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/auth/register") || path.equals("/api/auth/login") || path.equals("/api/health");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            var principal = auth.authenticate(token);
            if (principal.isEmpty()) {
                errors.write(request, response, HttpStatus.UNAUTHORIZED, "Your session has expired or is invalid. Sign in again.");
                return;
            }
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal.orElseThrow(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
