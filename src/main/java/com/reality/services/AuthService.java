package com.reality.services;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import com.reality.dto.auth.*;
import com.reality.entity.AuthSession;
import com.reality.entity.UserAccount;
import com.reality.repository.AuthSessionRepository;
import com.reality.repository.UserAccountRepository;
import com.reality.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String INVALID_CREDENTIALS = "Invalid username or password.";
    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthService(UserAccountRepository users, AuthSessionRepository sessions,
            PasswordEncoder passwords, CurrentUser currentUser, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.currentUser = currentUser;
        this.clock = clock;
        this.dummyPasswordHash = passwords.encode("invalid-account-placeholder-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null) throw badRequest("Enter your account details.");
        String username = normalizeUsername(request.username());
        if (!username.matches("[a-z0-9_.-]{3,40}")) {
            throw badRequest("Username must use 3–40 lowercase letters, digits, dots, underscores or hyphens.");
        }
        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        if (displayName.isBlank() || displayName.codePointCount(0, displayName.length()) > 80) {
            throw badRequest("Display name is required and must contain 80 characters or fewer.");
        }
        String password = request.password();
        if (password == null || password.codePointCount(0, password.length()) < 12 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("Password must contain at least 12 characters and no more than 72 UTF-8 bytes.");
        }
        if (users.existsByUsername(username)) throw usernameConflict();
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwords.encode(password));
        user.setCreatedAt(clock.instant());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw usernameConflict();
        }
        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null ||
                request.username().isBlank() || request.password().isEmpty()) {
            throw badRequest("Enter your username and password.");
        }
        String username = normalizeUsername(request.username());
        Optional<UserAccount> user = username.matches("[a-z0-9_.-]{3,40}")
                ? users.findByUsername(username) : Optional.empty();
        boolean permittedLength = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        String candidate = permittedLength ? request.password() : "invalid-overlength-password";
        String hash = user.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        boolean matched = passwords.matches(candidate, hash);
        if (!permittedLength || user.isEmpty() || !matched) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        return issueToken(user.orElseThrow());
    }

    @Transactional
    public Optional<UserPrincipal> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        Optional<AuthSession> found = sessions.findByTokenHashAndRevokedAtIsNull(TokenHashes.sha256(token));
        if (found.isEmpty()) return Optional.empty();
        AuthSession session = found.orElseThrow();
        if (!session.getExpiresAt().isAfter(clock.instant())) {
            session.setRevokedAt(clock.instant());
            sessions.save(session);
            return Optional.empty();
        }
        UserAccount user = session.getUser();
        return Optional.of(new UserPrincipal(user.getId(), user.getUsername()));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return userResponse(users.findById(currentUser.id())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Sign in again to continue.")));
    }

    @Transactional
    public void logout(String token) {
        Long userId = currentUser.id();
        sessions.findByTokenHashAndRevokedAtIsNull(TokenHashes.sha256(token))
                .filter(session -> session.getUser().getId().equals(userId))
                .ifPresent(session -> {
                    session.setRevokedAt(clock.instant());
                    sessions.save(session);
                });
    }

    private AuthResponse issueToken(UserAccount user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofDays(30));
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(TokenHashes.sha256(token));
        session.setCreatedAt(now);
        session.setExpiresAt(expiresAt);
        sessions.save(session);
        return new AuthResponse(token, expiresAt, userResponse(user));
    }

    private UserResponse userResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getCreatedAt());
    }
    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
    private AuthException badRequest(String message) { return new AuthException(HttpStatus.BAD_REQUEST, message); }
    private AuthException usernameConflict() { return new AuthException(HttpStatus.CONFLICT, "Username is already in use."); }
}
