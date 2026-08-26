package com.reality.services.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.reality.dto.SessionDayResponseDTO;
import com.reality.dto.SessionRequestDTO;
import com.reality.dto.SessionResponseDTO;
import com.reality.entity.Activity;
import com.reality.entity.Session;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.ActivityRepository;
import com.reality.repository.SessionRepository;
import com.reality.services.SessionService;

@Service
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final ActivityRepository activityRepository;

    public SessionServiceImpl(SessionRepository sessionRepository,
                              ActivityRepository activityRepository) {
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    public SessionResponseDTO startSession(SessionRequestDTO request) {

        Activity activity = activityRepository
                .findByIdAndActiveTrue(request.getActivityId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Active activity not found with id: "
                                        + request.getActivityId()));

        Session session = new Session();

        session.setActivity(activity);
        session.setStartTime(LocalDateTime.now());

        Session savedSession = sessionRepository.save(session);

        return mapToResponseDTO(savedSession);
    }
    
    @Override
    public SessionResponseDTO stopSession(Long sessionId) {

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Session not found with id: " + sessionId));

        if (session.getEndTime() != null) {
            throw new IllegalArgumentException(
                    "Session has already been stopped");
        }

        LocalDateTime endTime = LocalDateTime.now();

        session.setEndTime(endTime);

        long duration = Duration.between(
                session.getStartTime(),
                endTime
        ).getSeconds();

        session.setDuration(duration);

        Session savedSession = sessionRepository.save(session);

        return mapToResponseDTO(savedSession);
    }
    
    @Override
    public SessionResponseDTO getSessionById(Long id) {

        Session session = sessionRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Session not found with id: " + id));

        return mapToResponseDTO(session);
    }

    @Override
    public List<SessionResponseDTO> getAllSessions() {

        return sessionRepository.findAll()
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SessionResponseDTO> getSessionsByActivity(Long activityId) {

        activityRepository.findByIdAndActiveTrue(activityId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Active activity not found with id: "
                                        + activityId));

        return sessionRepository.findByActivityId(activityId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SessionDayResponseDTO getSessionsForDay(
            Long activityId,
            LocalDate date) {

        activityRepository.findByIdAndActiveTrue(activityId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Active activity not found with id: "
                                        + activityId));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Session> sessions =
                sessionRepository
                        .findByActivityIdAndStartTimeLessThanAndEndTimeGreaterThan(
                                activityId,
                                dayEnd,
                                dayStart);

        List<SessionResponseDTO> sessionResponses = sessions.stream()
                .map(this::mapToResponseDTO)
                .toList();

        long totalDuration = 0;

        for (Session session : sessions) {
            totalDuration += calculateOverlapDuration(
                    session,
                    dayStart,
                    dayEnd);
        }

        return new SessionDayResponseDTO(
                activityId,
                date,
                totalDuration,
                sessionResponses);
    }

    @Override
    public void deleteSession(Long id) {

        Session session = sessionRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Session not found with id: " + id));

        sessionRepository.delete(session);
    }

    private SessionResponseDTO mapToResponseDTO(Session session) {

        return new SessionResponseDTO(
                session.getId(),
                session.getActivity().getId(),
                session.getStartTime(),
                session.getEndTime(),
                session.getDuration()
        );
    }
    
    
    private long calculateOverlapDuration(
            Session session,
            LocalDateTime dayStart,
            LocalDateTime dayEnd) {

        LocalDateTime overlapStart =
                session.getStartTime().isAfter(dayStart)
                        ? session.getStartTime()
                        : dayStart;

        LocalDateTime overlapEnd =
                session.getEndTime().isBefore(dayEnd)
                        ? session.getEndTime()
                        : dayEnd;

        return Duration.between(overlapStart, overlapEnd).getSeconds();
    }
}