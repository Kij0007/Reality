package com.reality.dto;

import java.time.LocalDate;
import java.util.List;

public class SessionDayResponseDTO {

    private Long activityId;
    private LocalDate date;
    private Long totalDuration;
    private List<SessionResponseDTO> sessions;
    
    
    public SessionDayResponseDTO() {
    }

    public SessionDayResponseDTO(
            Long activityId,
            LocalDate date,
            Long totalDuration,
            List<SessionResponseDTO> sessions) {

        this.activityId = activityId;
        this.date = date;
        this.totalDuration = totalDuration;
        this.sessions = sessions;
    }

	public Long getActivityId() {
		return activityId;
	}

	public void setActivityId(Long activityId) {
		this.activityId = activityId;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public Long getTotalDuration() {
		return totalDuration;
	}

	public void setTotalDuration(Long totalDuration) {
		this.totalDuration = totalDuration;
	}

	public List<SessionResponseDTO> getSessions() {
		return sessions;
	}

	public void setSessions(List<SessionResponseDTO> sessions) {
		this.sessions = sessions;
	}

    
}