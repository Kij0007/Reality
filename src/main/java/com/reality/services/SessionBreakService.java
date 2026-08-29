package com.reality.services;

import java.util.List;

import com.reality.dto.BreakRequestDTO;
import com.reality.dto.BreakResponseDTO;

public interface SessionBreakService {

    BreakResponseDTO startBreak(
            Long sessionId,
            BreakRequestDTO request);

    BreakResponseDTO resumeSession(
            Long sessionId);

    List<BreakResponseDTO> getBreaksForSession(
            Long sessionId);
}