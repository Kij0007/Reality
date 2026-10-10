package com.reality.services.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reality.dto.BreakRequestDTO;
import com.reality.dto.BreakResponseDTO;
import com.reality.entity.Session;
import com.reality.entity.SessionBreak;
import com.reality.repository.SessionBreakRepository;
import com.reality.services.SessionBreakService;
import com.reality.services.OwnedResources;

@Service
@Transactional(readOnly = true)
public class SessionBreakServiceImpl
        implements SessionBreakService {

    private final OwnedResources owned;

    private final SessionBreakRepository sessionBreakRepository;

    public SessionBreakServiceImpl(
            OwnedResources owned,
            SessionBreakRepository sessionBreakRepository) {

        this.owned = owned;
        this.sessionBreakRepository = sessionBreakRepository;
    }

    @Override
    @Transactional
    public BreakResponseDTO startBreak(
            Long sessionId,
            BreakRequestDTO request) {

        Session session = owned.session(sessionId);

        // Already stopped
        if (session.getEndTime() != null) {
            throw new IllegalArgumentException(
                    "Cannot take break on a stopped session");
        }

        // Prevent two active breaks
        sessionBreakRepository
                .findBySessionIdAndSessionActivityOwnerIdAndEndTimeIsNull(sessionId, owned.ownerId())
                .ifPresent(existingBreak -> {
                    throw new IllegalArgumentException(
                            "Session is already on break");
                });

        SessionBreak sessionBreak =
                new SessionBreak();

        sessionBreak.setSession(session);

        sessionBreak.setStartTime(
                LocalDateTime.now());

        if (request != null) {
            sessionBreak.setDescription(
                    request.getDescription());
        }

        SessionBreak saved =
                sessionBreakRepository.save(sessionBreak);

        return mapToResponseDTO(saved);
    }

    @Override
    @Transactional
    public BreakResponseDTO resumeSession(
            Long sessionId) {

        Session session = owned.session(sessionId);

        if (session.getEndTime() != null) {
            throw new IllegalArgumentException(
                    "Session has already been stopped");
        }

        SessionBreak sessionBreak =
                sessionBreakRepository
                        .findBySessionIdAndSessionActivityOwnerIdAndEndTimeIsNull(
                                sessionId, owned.ownerId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Session is not currently on break"));

        LocalDateTime endTime =
                LocalDateTime.now();

        sessionBreak.setEndTime(endTime);

        long duration =
                Duration.between(
                        sessionBreak.getStartTime(),
                        endTime)
                        .getSeconds();

        sessionBreak.setDuration(duration);

        SessionBreak saved =
                sessionBreakRepository.save(sessionBreak);

        return mapToResponseDTO(saved);
    }

    @Override
    public List<BreakResponseDTO>
            getBreaksForSession(Long sessionId) {

        owned.session(sessionId);

        return sessionBreakRepository
                .findBySessionIdAndSessionActivityOwnerId(sessionId, owned.ownerId())
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    private BreakResponseDTO mapToResponseDTO(
            SessionBreak sessionBreak) {

        BreakResponseDTO response =
                new BreakResponseDTO();

        response.setId(sessionBreak.getId());

        response.setSessionId(
                sessionBreak.getSession().getId());

        response.setStartTime(
                sessionBreak.getStartTime());

        response.setEndTime(
                sessionBreak.getEndTime());

        response.setDuration(
                sessionBreak.getDuration());

        response.setDescription(
                sessionBreak.getDescription());

        return response;
    }
}
