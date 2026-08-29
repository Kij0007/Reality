package com.reality.services.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.reality.dto.DailyProgressResponseDTO;
import com.reality.dto.SessionDayResponseDTO;
import com.reality.entity.Activity;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.ActivityRepository;
import com.reality.services.DailyProgressService;
import com.reality.services.SessionService;

@Service
public class DailyProgressServiceImpl
        implements DailyProgressService {

    private final ActivityRepository activityRepository;
    private final SessionService sessionService;

    public DailyProgressServiceImpl(
            ActivityRepository activityRepository,
            SessionService sessionService) {

        this.activityRepository = activityRepository;
        this.sessionService = sessionService;
    }

    @Override
    public DailyProgressResponseDTO getDailyProgress(
            Long activityId,
            LocalDate date) {

        // 1. Find the activity
        Activity activity = activityRepository
                .findByIdAndActiveTrue(activityId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Active activity not found with id: "
                                        + activityId));

        // 2. Get total session duration for that day
        SessionDayResponseDTO sessionDay =
                sessionService.getSessionsForDay(
                        activityId,
                        date);

        long totalDuration =
                sessionDay.getTotalDuration();

        // minimumDuration is stored in minutes
        // Session duration is stored in seconds
        long requiredDuration =
                activity.getMinimumDuration() * 60L;

        // 3. Check whether daily target was completed
        boolean completed =
                totalDuration >= requiredDuration;
                
                

        // 4. Return response
        return new DailyProgressResponseDTO(
                activity.getId(),
                activity.getName(),
                date,
                activity.getMinimumDuration(),
                totalDuration,
                completed);
    }
}