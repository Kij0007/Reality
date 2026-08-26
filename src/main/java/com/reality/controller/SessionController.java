package com.reality.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.reality.dto.SessionDayResponseDTO;
import com.reality.dto.SessionRequestDTO;
import com.reality.dto.SessionResponseDTO;
import com.reality.services.SessionService;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    public ResponseEntity<SessionResponseDTO> startSession(
            @RequestBody SessionRequestDTO request) {

        SessionResponseDTO response =
                sessionService.startSession(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}/stop")
    public ResponseEntity<SessionResponseDTO> stopSession(
            @PathVariable Long id) {

        SessionResponseDTO response =
                sessionService.stopSession(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponseDTO> getSessionById(
            @PathVariable Long id) {

        SessionResponseDTO response =
                sessionService.getSessionById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SessionResponseDTO>> getAllSessions() {

        List<SessionResponseDTO> response =
                sessionService.getAllSessions();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<List<SessionResponseDTO>> getSessionsByActivity(
            @PathVariable Long activityId) {

        List<SessionResponseDTO> response =
                sessionService.getSessionsByActivity(activityId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/activity/{activityId}/date/{date}")
    public ResponseEntity<SessionDayResponseDTO> getSessionsForDay(
            @PathVariable Long activityId,
            @PathVariable LocalDate date) {

        SessionDayResponseDTO response =
                sessionService.getSessionsForDay(activityId, date);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long id) {

        sessionService.deleteSession(id);

        return ResponseEntity.noContent().build();
    }
}