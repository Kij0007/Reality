package com.reality.services.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.reality.dto.DailyProgressResponseDTO;
import com.reality.dto.StreakResponseDTO;
import com.reality.entity.Activity;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.ActivityRepository;
import com.reality.services.DailyProgressService;
import com.reality.services.StreakService;

@Service
public class StreakServiceImpl
        implements StreakService {

    private final ActivityRepository activityRepository;

    private final DailyProgressService dailyProgressService;

    public StreakServiceImpl(
            ActivityRepository activityRepository,
            DailyProgressService dailyProgressService) {

        this.activityRepository =
                activityRepository;

        this.dailyProgressService =
                dailyProgressService;
    }

    // ------------------------------------------------
    // GET CURRENT STREAK
    // ------------------------------------------------

    @Override
    public StreakResponseDTO getCurrentStreak(
            Long activityId) {

        // --------------------------------------------
        // 1. FIND ACTIVITY
        // --------------------------------------------

        Activity activity =
                activityRepository
                        .findByIdAndActiveTrue(activityId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Active activity not found with id: "
                                                + activityId));

        /*
         * Today is still in progress.
         *
         * Therefore we start streak calculation
         * from yesterday.
         */
        LocalDate lastEvaluatedDate =
                LocalDate.now().minusDays(1);

        LocalDate dateToCheck =
                lastEvaluatedDate;

        int currentStreak = 0;

        /*
         * We use startDate instead of createdAt.
         *
         * startDate = when commitment actually started
         * createdAt = when DB record was created
         */
        LocalDate activityStartDate =
                activity.getStartDate();

        // --------------------------------------------
        // 2. CHECK DAYS BACKWARDS
        // --------------------------------------------

        while (!dateToCheck.isBefore(
                activityStartDate)) {

            DayOfWeek dayOfWeek =
                    dateToCheck.getDayOfWeek();

            /*
             * Check whether the current date is
             * one of the user's selected scheduled days.
             *
             * Example:
             *
             * scheduledDays =
             * MONDAY, WEDNESDAY, FRIDAY
             *
             * If dateToCheck = THURSDAY
             * → ignore it.
             */
            if (!activity.getScheduledDays()
                    .contains(dayOfWeek)) {

                dateToCheck =
                        dateToCheck.minusDays(1);

                continue;
            }

            /*
             * This IS a scheduled activity day.
             *
             * Ask DailyProgressService whether
             * the user completed the minimum duration.
             */
            DailyProgressResponseDTO progress =
                    dailyProgressService
                            .getDailyProgress(
                                    activityId,
                                    dateToCheck);

            /*
             * Scheduled day was not completed.
             *
             * Streak ends immediately.
             */
            if (!progress.getCompleted()) {
                break;
            }

            /*
             * Scheduled day completed successfully.
             */
            currentStreak++;

            /*
             * Move one calendar day backwards.
             */
            dateToCheck =
                    dateToCheck.minusDays(1);
        }

        // --------------------------------------------
        // 3. RETURN STREAK
        // --------------------------------------------

        return new StreakResponseDTO(
                activity.getId(),
                activity.getName(),
                currentStreak,
                lastEvaluatedDate);
    }
}