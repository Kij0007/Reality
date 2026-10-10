package com.reality.security;

import java.time.Clock;
import com.reality.services.AuthService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SecurityConfiguration {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public Clock authClock() { return Clock.systemUTC(); }

    // Suppress Boot's generated development account. Credentials are checked by
    // AuthService and every protected request requires a persisted bearer token.
    @Bean public AuthenticationManager noImplicitLogin() {
        return authentication -> { throw new BadCredentialsException("Bearer authentication is required."); };
    }

    @Bean
    public SecurityFilterChain realitySecurity(HttpSecurity http, AuthService auth,
            SecurityErrorWriter errors) throws Exception {
        http
            // Tokens are explicit Authorization headers; there are no ambient auth cookies.
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, failure) ->
                    errors.write(request, response, HttpStatus.UNAUTHORIZED, "Sign in to use Reality."))
                .accessDeniedHandler((request, response, failure) ->
                    errors.write(request, response, HttpStatus.FORBIDDEN, "You do not have access to this operation.")))
            .authorizeHttpRequests(requests -> requests
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/css/**", "/js/**", "/favicon.svg", "/api/health").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new BearerTokenFilter(auth, errors), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AuthRateLimitFilter(errors), BearerTokenFilter.class);
        return http.build();
    }
}
