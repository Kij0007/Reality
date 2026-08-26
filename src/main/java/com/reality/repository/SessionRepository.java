package com.reality.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reality.entity.Session;

public interface SessionRepository extends JpaRepository<Session, Long> {
	
	
	  List<Session> findByActivityId(Long activityId);

    List<Session> findByActivityIdAndStartTimeLessThanAndEndTimeGreaterThan(
            Long activityId,
            LocalDateTime dayEnd,
            LocalDateTime dayStart);
}