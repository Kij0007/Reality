package com.reality.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reality.dto.StreakResponseDTO;
import com.reality.services.StreakService;

@RestController
@RequestMapping("/api/streaks")
public class StreakController {

    private final StreakService streakService;

    public StreakController(
            StreakService streakService) {

        this.streakService = streakService;
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<StreakResponseDTO>
            getCurrentStreak(
                    @PathVariable Long activityId) {

        StreakResponseDTO response =
                streakService.getCurrentStreak(activityId);

        return ResponseEntity.ok(response);
    }
}