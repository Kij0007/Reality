package com.reality.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reality.dto.DailyProgressResponseDTO;
import com.reality.services.DailyProgressService;

@RestController
@RequestMapping("/api/daily-progress")
public class DailyProgressController {

    private final DailyProgressService dailyProgressService;

    public DailyProgressController(
            DailyProgressService dailyProgressService) {

        this.dailyProgressService = dailyProgressService;
        

        
    }

    @GetMapping("/activity/{activityId}/date/{date}")
    public ResponseEntity<DailyProgressResponseDTO>
            getDailyProgress(
                    @PathVariable Long activityId,
                    @PathVariable LocalDate date) {

        DailyProgressResponseDTO response =
                dailyProgressService.getDailyProgress(
                        activityId,
                        date);

        return ResponseEntity.ok(response);
    }
}