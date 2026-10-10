package com.reality.services.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reality.dto.SessionDayResponseDTO;
import com.reality.dto.SessionRequestDTO;
import com.reality.dto.SessionResponseDTO;
import com.reality.entity.Activity;
import com.reality.entity.Session;
import com.reality.entity.SessionBreak;
import com.reality.repository.SessionBreakRepository;
import com.reality.repository.SessionRepository;
import com.reality.services.SessionService;
import com.reality.services.OwnedResources;

@Service
@Transactional(readOnly = true)
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final OwnedResources owned;
    private final SessionBreakRepository sessionBreakRepository;

    public SessionServiceImpl(SessionRepository sessionRepository,
                              OwnedResources owned, SessionBreakRepository sessionBreakRepository) {
        this.sessionRepository = sessionRepository;
        this.owned = owned;
        this.sessionBreakRepository = sessionBreakRepository;
    }

    @Override
    @Transactional
    public SessionResponseDTO startSession(SessionRequestDTO request) {

        Activity activity = owned.activeActivity(request.getActivityId());

        Session session = new Session();

        session.setActivity(activity);
        session.setStartTime(LocalDateTime.now());

        Session savedSession = sessionRepository.save(session);

        return mapToResponseDTO(savedSession);
    }
    
    @Override
    @Transactional
    public SessionResponseDTO stopSession(Long sessionId) {

        // 1. Find session
        Session session = owned.session(sessionId);

        // 2. Prevent stopping the same session twice
        if (session.getEndTime() != null) {

            throw new IllegalArgumentException(
                    "Session has already been stopped");
        }

        // 3. Capture session end time
        LocalDateTime endTime =
                LocalDateTime.now();

        session.setEndTime(endTime);

        // ------------------------------------------------
        // 4. IF SESSION IS CURRENTLY ON BREAK,
        //    CLOSE THAT BREAK AUTOMATICALLY
        // ------------------------------------------------

        sessionBreakRepository
                .findBySessionIdAndSessionActivityOwnerIdAndEndTimeIsNull(sessionId, owned.ownerId())
                .ifPresent(activeBreak -> {

                    activeBreak.setEndTime(endTime);

                    long breakDuration =
                            Duration.between(
                                    activeBreak.getStartTime(),
                                    endTime)
                                    .getSeconds();

                    activeBreak.setDuration(
                            breakDuration);

                    sessionBreakRepository.save(
                            activeBreak);
                });

        // ------------------------------------------------
        // 5. CALCULATE TOTAL ELAPSED TIME
        // ------------------------------------------------

        long totalElapsedDuration =
                Duration.between(
                        session.getStartTime(),
                        endTime)
                        .getSeconds();

        // ------------------------------------------------
        // 6. CALCULATE TOTAL BREAK TIME
        // ------------------------------------------------

        long totalBreakDuration =
                sessionBreakRepository
                        .findBySessionIdAndSessionActivityOwnerId(sessionId, owned.ownerId())
                        .stream()
                        .filter(sessionBreak ->
                                sessionBreak.getDuration() != null)
                        .mapToLong(
                                SessionBreak::getDuration)
                        .sum();

        // ------------------------------------------------
        // 7. CALCULATE ACTUAL WORKING DURATION
        // ------------------------------------------------

        long activeDuration =
                totalElapsedDuration
                        - totalBreakDuration;

        session.setDuration(activeDuration);

        // ------------------------------------------------
        // 8. SAVE SESSION
        // ------------------------------------------------

        Session savedSession =
                sessionRepository.save(session);

        return mapToResponseDTO(savedSession);
    }
    
    @Override
    public SessionResponseDTO getSessionById(Long id) {

        Session session = owned.session(id);

        return mapToResponseDTO(session);
    }

    @Override
    public List<SessionResponseDTO> getAllSessions() {

        return sessionRepository.findByActivityOwnerId(owned.ownerId())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SessionResponseDTO> getSessionsByActivity(Long activityId) {

        owned.activity(activityId);

        return sessionRepository.findByActivityIdAndActivityOwnerId(activityId, owned.ownerId())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SessionDayResponseDTO getSessionsForDay(
            Long activityId,
            LocalDate date) {

        owned.activity(activityId);

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Session> sessions =
                sessionRepository
                        .findByActivityIdAndActivityOwnerIdAndStartTimeLessThanAndEndTimeGreaterThan(
                                activityId,
                                owned.ownerId(),
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
    @Transactional
    public void deleteSession(Long id) {

        Session session = owned.session(id);

        // Break rows reference this session and must be removed first.
        sessionBreakRepository.deleteAll(
                sessionBreakRepository.findBySessionIdAndSessionActivityOwnerId(id, owned.ownerId()));
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

        long elapsedDuration =
                Duration.between(overlapStart, overlapEnd).getSeconds();

        long breakDuration = 0;

        for (SessionBreak sessionBreak :
                sessionBreakRepository.findBySessionIdAndSessionActivityOwnerId(session.getId(), owned.ownerId())) {

            LocalDateTime breakStart =
                    sessionBreak.getStartTime().isAfter(overlapStart)
                            ? sessionBreak.getStartTime()
                            : overlapStart;

            LocalDateTime recordedBreakEnd =
                    sessionBreak.getEndTime() != null
                            ? sessionBreak.getEndTime()
                            : session.getEndTime();

            LocalDateTime breakEnd =
                    recordedBreakEnd.isBefore(overlapEnd)
                            ? recordedBreakEnd
                            : overlapEnd;

            // A break on another day contributes no time to this overlap.
            if (breakEnd.isAfter(breakStart)) {
                breakDuration +=
                        Duration.between(breakStart, breakEnd).getSeconds();
            }
        }

        return Math.max(0, elapsedDuration - breakDuration);
    }
}
