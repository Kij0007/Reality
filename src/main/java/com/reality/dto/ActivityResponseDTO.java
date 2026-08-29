package com.reality.dto;

import java.time.LocalDateTime;

import com.reality.enums.ActivityCategory;

import lombok.Data;
import java.time.LocalDate;

import java.time.DayOfWeek;
import java.util.Set;

@Data
public class ActivityResponseDTO {

    private Long id;

    private String name;

    private Integer minimumDuration;

    private ActivityCategory category;

    private Boolean active;
    
    private LocalDate startDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    
    private Set<DayOfWeek> scheduledDays;
    
    public Set<DayOfWeek> getScheduledDays() {
        return scheduledDays;
    }

    public void setScheduledDays(Set<DayOfWeek> scheduledDays) {
        this.scheduledDays = scheduledDays;
    }

	public ActivityResponseDTO() {
		super();
	}

	public Long getId() {
		return id;
	}
	
	public LocalDate getStartDate() {
	    return startDate;
	}

	public void setStartDate(LocalDate startDate) {
	    this.startDate = startDate;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getMinimumDuration() {
		return minimumDuration;
	}

	public void setMinimumDuration(Integer minimumDuration) {
		this.minimumDuration = minimumDuration;
	}

	public ActivityCategory getCategory() {
		return category;
	}

	public void setCategory(ActivityCategory category) {
		this.category = category;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public ActivityResponseDTO(Long id, String name, Integer minimumDuration, ActivityCategory category, Boolean active,
			LocalDateTime createdAt, LocalDateTime updatedAt,Set<DayOfWeek> scheduledDays, LocalDate startDate ) {
		super();
		this.id = id;
		this.name = name;
		this.minimumDuration = minimumDuration;
		this.category = category;
		this.active = active;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.scheduledDays = scheduledDays;
		this.startDate = startDate;
	}

}