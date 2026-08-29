package com.reality.dto;

import lombok.Data;

import com.reality.enums.ActivityCategory;
import com.reality.exception.InvalidActivityException;
import java.time.DayOfWeek;
import java.util.Set;
import java.time.LocalDate;


@Data
public class ActivityRequestDTO {

    private String name;

    private Integer minimumDuration;
    private Set<DayOfWeek> scheduledDays;
    private LocalDate startDate;
    
    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    

    private ActivityCategory category;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getMinimumDuration() {
		return minimumDuration;
	}
	
	public Set<DayOfWeek> getScheduledDays() {
	    return scheduledDays;
	}

	public void setScheduledDays(Set<DayOfWeek> scheduledDays) {
	    this.scheduledDays = scheduledDays;
	}

	public void setMinimumDuration(Integer minimumDuration) {
		
		 if (minimumDuration == null || minimumDuration <= 0) {
		        throw new InvalidActivityException(
		                "Minimum duration must be greater than zero.");
		    }
		
		this.minimumDuration = minimumDuration;
	}

	public ActivityCategory getCategory() {
		return category;
	}

	public void setCategory(ActivityCategory category) {
		this.category = category;
	}

	public ActivityRequestDTO(String name, Integer minimumDuration, ActivityCategory category,LocalDate startDate ) {
		super();
		this.name = name;
		this.minimumDuration = minimumDuration;
		this.category = category;
		this.startDate = startDate;
	}

	public ActivityRequestDTO() {
		super();
	}

}