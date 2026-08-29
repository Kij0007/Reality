package com.reality.entity;


import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.LocalDate;

import com.reality.enums.ActivityCategory;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "activities")
public class Activity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    private LocalDate startDate;

    private Integer minimumDuration;

    @Enumerated(EnumType.STRING)
    private ActivityCategory category;

    private Boolean active = true;
    
    @ElementCollection
    @CollectionTable(
            name = "activity_scheduled_days",
            joinColumns = @JoinColumn(name = "activity_id")
    )
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> scheduledDays = new HashSet<>();
    
    
    
    public Activity() {
		super();
	}

    public Activity(
            Long id,
            String name,
            Integer minimumDuration,
            ActivityCategory category,
            Boolean active,
            LocalDate startDate,
            Set<DayOfWeek> scheduledDays,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        super();

        this.id = id;
        this.name = name;
        this.minimumDuration = minimumDuration;
        this.category = category;
        this.active = active;
        this.startDate = startDate;
        this.scheduledDays = scheduledDays;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
	
	public LocalDate getStartDate() {
	    return startDate;
	}

	public void setStartDate(LocalDate startDate) {
	    this.startDate = startDate;
	}

	public Long getId() {
		return id;
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

	private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    
    
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    
    public Set<DayOfWeek> getScheduledDays() {
        return scheduledDays;
    }

    public void setScheduledDays(Set<DayOfWeek> scheduledDays) {
        this.scheduledDays = scheduledDays;
    }

	
    

}