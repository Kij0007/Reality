package com.reality.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reality.entity.SessionBreak;

public interface SessionBreakRepository
        extends JpaRepository<SessionBreak, Long> {

    Optional<SessionBreak>
            findBySessionIdAndSessionActivityOwnerIdAndEndTimeIsNull(Long sessionId, Long ownerId);

    List<SessionBreak>
            findBySessionIdAndSessionActivityOwnerId(Long sessionId, Long ownerId);
}
