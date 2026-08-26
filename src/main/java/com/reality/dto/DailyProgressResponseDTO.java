package com.reality.dto;

import java.time.LocalDate;

public class DailyProgressResponseDTO {

    private Long activityId;

    private String activityName;

    private LocalDate date;

    private Integer minimumDuration;

    private Long totalDuration;

    private Boolean completed;

    public DailyProgressResponseDTO() {
    }

    public DailyProgressResponseDTO(
            Long activityId,
            String activityName,
            LocalDate date,
            Integer minimumDuration,
            Long totalDuration,
            Boolean completed) {

        this.activityId = activityId;
        this.activityName = activityName;
        this.date = date;
        this.minimumDuration = minimumDuration;
        this.totalDuration = totalDuration;
        this.completed = completed;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getMinimumDuration() {
        return minimumDuration;
    }

    public void setMinimumDuration(Integer minimumDuration) {
        this.minimumDuration = minimumDuration;
    }

    public Long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }
}