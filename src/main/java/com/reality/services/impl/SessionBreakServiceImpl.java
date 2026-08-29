package com.reality.services.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.reality.dto.BreakRequestDTO;
import com.reality.dto.BreakResponseDTO;
import com.reality.entity.Session;
import com.reality.entity.SessionBreak;
import com.reality.exception.InvalidActivityException;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.SessionBreakRepository;
import com.reality.repository.SessionRepository;
import com.reality.services.SessionBreakService;

@Service
public class SessionBreakServiceImpl
        implements SessionBreakService {

    private final SessionRepository sessionRepository;

    private final SessionBreakRepository sessionBreakRepository;

    public SessionBreakServiceImpl(
            SessionRepository sessionRepository,
            SessionBreakRepository sessionBreakRepository) {

        this.sessionRepository = sessionRepository;
        this.sessionBreakRepository = sessionBreakRepository;
    }

    @Override
    public BreakResponseDTO startBreak(
            Long sessionId,
            BreakRequestDTO request) {

        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Session not found with id: "
                                        + sessionId));

        // Already stopped
        if (session.getEndTime() != null) {
            throw new IllegalArgumentException(
                    "Cannot take break on a stopped session");
        }

        // Prevent two active breaks
        sessionBreakRepository
                .findBySessionIdAndEndTimeIsNull(sessionId)
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
    public BreakResponseDTO resumeSession(
            Long sessionId) {

        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Session not found with id: "
                                        + sessionId));

        if (session.getEndTime() != null) {
            throw new IllegalArgumentException(
                    "Session has already been stopped");
        }

        SessionBreak sessionBreak =
                sessionBreakRepository
                        .findBySessionIdAndEndTimeIsNull(
                                sessionId)
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

        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException(
                    "Session not found with id: "
                            + sessionId);
        }

        return sessionBreakRepository
                .findBySessionId(sessionId)
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