package com.reality.repository;

import java.util.Optional;
import com.reality.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);
    Optional<AuthSession> findByTokenHash(String tokenHash);
}
