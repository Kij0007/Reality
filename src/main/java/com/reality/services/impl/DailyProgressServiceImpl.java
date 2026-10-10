package com.reality.services.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reality.dto.DailyProgressResponseDTO;
import com.reality.dto.SessionDayResponseDTO;
import com.reality.entity.Activity;
import com.reality.services.DailyProgressService;
import com.reality.services.SessionService;
import com.reality.services.OwnedResources;

@Service
@Transactional(readOnly = true)
public class DailyProgressServiceImpl
        implements DailyProgressService {

    private final OwnedResources owned;
    private final SessionService sessionService;

    public DailyProgressServiceImpl(
            OwnedResources owned,
            SessionService sessionService) {

        this.owned = owned;
        this.sessionService = sessionService;
    }

    @Override
    public DailyProgressResponseDTO getDailyProgress(
            Long activityId,
            LocalDate date) {

        // 1. Find the activity
        Activity activity = owned.activeActivity(activityId);

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
