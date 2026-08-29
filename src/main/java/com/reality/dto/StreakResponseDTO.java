package com.reality.dto;

import java.time.LocalDate;

public class StreakResponseDTO {

    private Long activityId;
    private String activityName;
    private Integer currentStreak;
    private LocalDate lastEvaluatedDate;

    public StreakResponseDTO() {
    }

    public StreakResponseDTO(
            Long activityId,
            String activityName,
            Integer currentStreak,
            LocalDate lastEvaluatedDate) {

        this.activityId = activityId;
        this.activityName = activityName;
        this.currentStreak = currentStreak;
        this.lastEvaluatedDate = lastEvaluatedDate;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public Integer getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(Integer currentStreak) {
        this.currentStreak = currentStreak;
    }

    public LocalDate getLastEvaluatedDate() {
        return lastEvaluatedDate;
    }

    public void setLastEvaluatedDate(LocalDate lastEvaluatedDate) {
        this.lastEvaluatedDate = lastEvaluatedDate;
    }
}