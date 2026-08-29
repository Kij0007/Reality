package com.reality.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reality.entity.SessionBreak;

public interface SessionBreakRepository
        extends JpaRepository<SessionBreak, Long> {

    Optional<SessionBreak>
            findBySessionIdAndEndTimeIsNull(Long sessionId);

    List<SessionBreak>
            findBySessionId(Long sessionId);
}