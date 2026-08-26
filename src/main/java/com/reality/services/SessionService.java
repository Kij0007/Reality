package com.reality.services;

import java.time.LocalDate;
import java.util.List;

import com.reality.dto.SessionDayResponseDTO;
import com.reality.dto.SessionRequestDTO;
import com.reality.dto.SessionResponseDTO;

public interface SessionService {

    // Create a completed study session
	 SessionResponseDTO startSession(SessionRequestDTO request);

	    SessionResponseDTO stopSession(Long sessionId);

    // Get a session by its ID
    SessionResponseDTO getSessionById(Long id);

    // Get all sessions
    List<SessionResponseDTO> getAllSessions();

    // Get all sessions belonging to a particular activity
    List<SessionResponseDTO> getSessionsByActivity(Long activityId);

    // Get sessions that contribute to a particular calendar day
    SessionDayResponseDTO getSessionsForDay(Long activityId, LocalDate date);

    // Delete a session
    void deleteSession(Long id);
}