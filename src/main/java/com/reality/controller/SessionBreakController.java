package com.reality.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reality.dto.BreakRequestDTO;
import com.reality.dto.BreakResponseDTO;
import com.reality.services.SessionBreakService;

@RestController
@RequestMapping("/api/sessions")
public class SessionBreakController {

    private final SessionBreakService sessionBreakService;

    public SessionBreakController(
            SessionBreakService sessionBreakService) {

        this.sessionBreakService =
                sessionBreakService;
    }

    @PostMapping("/{sessionId}/break")
    public ResponseEntity<BreakResponseDTO>
            startBreak(
                    @PathVariable Long sessionId,
                    @RequestBody(required = false)
                    BreakRequestDTO request) {

        BreakResponseDTO response =
                sessionBreakService.startBreak(
                        sessionId,
                        request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{sessionId}/resume")
    public ResponseEntity<BreakResponseDTO>
            resumeSession(
                    @PathVariable Long sessionId) {

        return ResponseEntity.ok(
                sessionBreakService
                        .resumeSession(sessionId));
    }

    @GetMapping("/{sessionId}/breaks")
    public ResponseEntity<List<BreakResponseDTO>>
            getBreaks(
                    @PathVariable Long sessionId) {

        return ResponseEntity.ok(
                sessionBreakService
                        .getBreaksForSession(sessionId));
    }
}