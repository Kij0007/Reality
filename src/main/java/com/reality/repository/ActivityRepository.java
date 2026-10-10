package com.reality.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.reality.entity.Activity;


@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
	
	
	List<Activity> findByOwnerIdAndActiveTrue(Long ownerId);
	
	Optional<Activity> findByIdAndOwnerIdAndActiveTrue(Long id, Long ownerId);

    Optional<Activity> findByIdAndOwnerId(Long id, Long ownerId);

}
