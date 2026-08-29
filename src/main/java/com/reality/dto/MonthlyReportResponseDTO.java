package com.reality.dto;

public class MonthlyReportResponseDTO {

    private Long activityId;
    private String activityName;

    private Integer year;
    private Integer month;

    // Total actual working time in seconds
    private Long totalDuration;

    private Integer totalSessions;

    // Only scheduled activity days
    private Integer scheduledDays;

    private Integer completedDays;
    private Integer missedDays;

    private Double completionPercentage;

    private Integer currentStreak;
    private Integer longestStreak;

    public MonthlyReportResponseDTO() {
    }

    public MonthlyReportResponseDTO(
            Long activityId,
            String activityName,
            Integer year,
            Integer month,
            Long totalDuration,
            Integer totalSessions,
            Integer scheduledDays,
            Integer completedDays,
            Integer missedDays,
            Double completionPercentage,
            Integer currentStreak,
            Integer longestStreak) {

        this.activityId = activityId;
        this.activityName = activityName;
        this.year = year;
        this.month = month;
        this.totalDuration = totalDuration;
        this.totalSessions = totalSessions;
        this.scheduledDays = scheduledDays;
        this.completedDays = completedDays;
        this.missedDays = missedDays;
        this.completionPercentage = completionPercentage;
        this.currentStreak = currentStreak;
        this.longestStreak = longestStreak;
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

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Integer getScheduledDays() {
        return scheduledDays;
    }

    public void setScheduledDays(Integer scheduledDays) {
        this.scheduledDays = scheduledDays;
    }

    public Integer getCompletedDays() {
        return completedDays;
    }

    public void setCompletedDays(Integer completedDays) {
        this.completedDays = completedDays;
    }

    public Integer getMissedDays() {
        return missedDays;
    }

    public void setMissedDays(Integer missedDays) {
        this.missedDays = missedDays;
    }

    public Double getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(
            Double completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public Integer getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(Integer currentStreak) {
        this.currentStreak = currentStreak;
    }

    public Integer getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(Integer longestStreak) {
        this.longestStreak = longestStreak;
    }
}