package com.reality.entity;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "reality_auth_sessions", indexes = {
    @Index(name = "idx_auth_expiry", columnList = "expires_at"),
    @Index(name = "idx_auth_token", columnList = "token_hash", unique = true)
})
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public AuthSession() { }
    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String hash) { tokenHash = hash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant time) { createdAt = time; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant time) { expiresAt = time; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant time) { revokedAt = time; }
}
