package com.reality.dto;

import lombok.Data;

import com.reality.enums.ActivityCategory;
import com.reality.exception.InvalidActivityException;


@Data
public class ActivityRequestDTO {

    private String name;

    private Integer minimumDuration;

    

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

	public ActivityRequestDTO(String name, Integer minimumDuration, ActivityCategory category) {
		super();
		this.name = name;
		this.minimumDuration = minimumDuration;
		this.category = category;
	}

	public ActivityRequestDTO() {
		super();
	}

}