package com.reality.dto;

import java.time.LocalDateTime;

public class SessionResponseDTO {

    private Long id;

    private Long activityId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long duration;

    public SessionResponseDTO() {
    }

    public SessionResponseDTO(Long id, Long activityId,
                              LocalDateTime startTime,
                              LocalDateTime endTime,
                              Long duration) {
        this.id = id;
        this.activityId = activityId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }
}