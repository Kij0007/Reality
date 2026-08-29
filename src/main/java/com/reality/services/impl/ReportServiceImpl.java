package com.reality.services.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import com.reality.dto.DailyProgressResponseDTO;
import com.reality.dto.MonthlyReportResponseDTO;
import com.reality.dto.StreakResponseDTO;
import com.reality.entity.Activity;
import com.reality.entity.Session;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.ActivityRepository;
import com.reality.repository.SessionRepository;
import com.reality.services.DailyProgressService;
import com.reality.services.ReportService;
import com.reality.services.StreakService;

@Service
public class ReportServiceImpl implements ReportService {

    private final ActivityRepository activityRepository;
    private final SessionRepository sessionRepository;
    private final DailyProgressService dailyProgressService;
    private final StreakService streakService;

    public ReportServiceImpl(
            ActivityRepository activityRepository,
            SessionRepository sessionRepository,
            DailyProgressService dailyProgressService,
            StreakService streakService) {

        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.dailyProgressService = dailyProgressService;
        this.streakService = streakService;
    }

    @Override
    public MonthlyReportResponseDTO getMonthlyReport(
            Long activityId,
            YearMonth month) {

        // -----------------------------------------
        // 1. FIND ACTIVITY
        // -----------------------------------------

        Activity activity = activityRepository
                .findByIdAndActiveTrue(activityId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Active activity not found with id: "
                                        + activityId));

        // -----------------------------------------
        // 2. MONTH BOUNDARIES
        // -----------------------------------------

        LocalDate monthStart =
                month.atDay(1);

        LocalDate monthEnd =
                month.atEndOfMonth();

        /*
         * Don't calculate anything before
         * the commitment's start date.
         */
        LocalDate calculationStart =
                monthStart.isBefore(activity.getStartDate())
                        ? activity.getStartDate()
                        : monthStart;

        /*
         * Duration/report totals may include today,
         * because we want the monthly time to update
         * while the user is using the app.
         */
        LocalDate today =
                LocalDate.now();

        LocalDate durationEnd =
                monthEnd.isAfter(today)
                        ? today
                        : monthEnd;

        /*
         * Completion / missed / streak logic should
         * only use fully finished days.
         *
         * Today must not yet count as missed.
         */
        LocalDate yesterday =
                today.minusDays(1);

        LocalDate evaluationEnd =
                monthEnd.isAfter(yesterday)
                        ? yesterday
                        : monthEnd;

        long totalDuration = 0;

        int scheduledDays = 0;
        int completedDays = 0;

        int longestStreak = 0;
        int runningStreak = 0;

        // -----------------------------------------
        // 3. CALCULATE MONTHLY DAILY METRICS
        // -----------------------------------------

        if (!calculationStart.isAfter(durationEnd)) {

            LocalDate date =
                    calculationStart;

            while (!date.isAfter(durationEnd)) {

                DailyProgressResponseDTO progress =
                        dailyProgressService
                                .getDailyProgress(
                                        activityId,
                                        date);

                /*
                 * Total time includes any work,
                 * even if the user worked on an
                 * unscheduled day.
                 */
                totalDuration +=
                        progress.getTotalDuration();

                /*
                 * Today is allowed in totalDuration,
                 * but today is NOT yet used for
                 * completed/missed/streak calculations.
                 */
                if (!date.isAfter(evaluationEnd)) {

                    boolean scheduled =
                            activity.getScheduledDays()
                                    .contains(
                                            date.getDayOfWeek());

                    if (scheduled) {

                        scheduledDays++;

                        if (progress.getCompleted()) {

                            completedDays++;

                            runningStreak++;

                            if (runningStreak
                                    > longestStreak) {

                                longestStreak =
                                        runningStreak;
                            }

                        } else {

                            /*
                             * Failed scheduled day
                             * breaks the streak.
                             */
                            runningStreak = 0;
                        }
                    }

                    /*
                     * Non-scheduled days are ignored:
                     *
                     * - don't increase streak
                     * - don't reset streak
                     */
                }

                date = date.plusDays(1);
            }
        }

        // -----------------------------------------
        // 4. MISSED DAYS
        // -----------------------------------------

        int missedDays =
                scheduledDays - completedDays;

        // -----------------------------------------
        // 5. COMPLETION PERCENTAGE
        // -----------------------------------------

        double completionPercentage = 0.0;

        if (scheduledDays > 0) {

            completionPercentage =
                    ((double) completedDays
                            / scheduledDays)
                            * 100;

            completionPercentage =
                    Math.round(
                            completionPercentage * 100.0)
                            / 100.0;
        }

        // -----------------------------------------
        // 6. TOTAL SESSION COUNT FOR MONTH
        // -----------------------------------------

        int totalSessions = 0;

        if (!calculationStart.isAfter(durationEnd)) {

            LocalDateTime rangeStart =
                    calculationStart.atStartOfDay();

            LocalDateTime rangeEnd =
                    durationEnd
                            .plusDays(1)
                            .atStartOfDay();

            /*
             * We already created this repository
             * query for overlap logic.
             *
             * It works for any date range,
             * not just one day.
             */
            List<Session> sessions =
                    sessionRepository
                            .findByActivityIdAndStartTimeLessThanAndEndTimeGreaterThan(
                                    activityId,
                                    rangeEnd,
                                    rangeStart);

            totalSessions =
                    sessions.size();
        }

        // -----------------------------------------
        // 7. CURRENT STREAK
        // -----------------------------------------

        StreakResponseDTO streak =
                streakService
                        .getCurrentStreak(activityId);

        // -----------------------------------------
        // 8. BUILD RESPONSE
        // -----------------------------------------

        return new MonthlyReportResponseDTO(
                activity.getId(),
                activity.getName(),
                month.getYear(),
                month.getMonthValue(),
                totalDuration,
                totalSessions,
                scheduledDays,
                completedDays,
                missedDays,
                completionPercentage,
                streak.getCurrentStreak(),
                longestStreak);
    }
}