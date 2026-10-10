package com.reality.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reality.entity.Session;

public interface SessionRepository extends JpaRepository<Session, Long> {
	
	
    List<Session> findByActivityOwnerId(Long ownerId);

    Optional<Session> findByIdAndActivityOwnerId(Long id, Long ownerId);

    List<Session> findByActivityIdAndActivityOwnerId(Long activityId, Long ownerId);

    List<Session> findByActivityIdAndActivityOwnerIdAndStartTimeLessThanAndEndTimeGreaterThan(
            Long activityId,
            Long ownerId,
            LocalDateTime dayEnd,
            LocalDateTime dayStart);
}
