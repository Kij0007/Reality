package com.reality.services.impl;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.reality.dto.ActivityRequestDTO;
import com.reality.dto.ActivityResponseDTO;
import com.reality.entity.Activity;
import com.reality.exception.InvalidActivityException;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.ActivityRepository;
import com.reality.services.ActivityService;

@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityServiceImpl(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    // ------------------------------------------------
    // CREATE ACTIVITY
    // ------------------------------------------------

    @Override
    public ActivityResponseDTO createActivity(
            ActivityRequestDTO request) {

        validateActivityRequest(request);

        Activity activity = new Activity();

        activity.setName(request.getName());

        activity.setMinimumDuration(
                request.getMinimumDuration());

        activity.setCategory(
                request.getCategory());

        activity.setStartDate(
                request.getStartDate());

        activity.setScheduledDays(
                new HashSet<>(request.getScheduledDays()));

        activity.setActive(true);

        Activity savedActivity =
                activityRepository.save(activity);

        return mapToResponseDTO(savedActivity);
    }

    // ------------------------------------------------
    // GET ALL ACTIVITIES
    // ------------------------------------------------

    @Override
    public List<ActivityResponseDTO> getAllActivities() {

        List<Activity> activities =
                activityRepository.findByActiveTrue();

        return activities.stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    // ------------------------------------------------
    // GET ACTIVITY BY ID
    // ------------------------------------------------

    @Override
    public ActivityResponseDTO getActivityById(Long id) {

        Activity activity =
                activityRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Active activity not found with id: "
                                                + id));

        return mapToResponseDTO(activity);
    }

    // ------------------------------------------------
    // UPDATE ACTIVITY
    // ------------------------------------------------

    @Override
    public ActivityResponseDTO updateActivity(
            Long id,
            ActivityRequestDTO request) {

        validateActivityRequest(request);

        Activity activity =
                activityRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Active activity not found with id: "
                                                + id));

        activity.setName(
                request.getName());

        activity.setMinimumDuration(
                request.getMinimumDuration());

        activity.setCategory(
                request.getCategory());

        activity.setStartDate(
                request.getStartDate());

        activity.setScheduledDays(
                new HashSet<>(request.getScheduledDays()));

        Activity updatedActivity =
                activityRepository.save(activity);

        return mapToResponseDTO(updatedActivity);
    }

    // ------------------------------------------------
    // DELETE ACTIVITY
    // SOFT DELETE
    // ------------------------------------------------

    @Override
    public void deleteActivity(Long id) {

        Activity activity =
                activityRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Active activity not found with id: "
                                                + id));

        activity.setActive(false);

        activityRepository.save(activity);
    }

    // ------------------------------------------------
    // VALIDATION
    // ------------------------------------------------

    private void validateActivityRequest(
            ActivityRequestDTO request) {

        if (request.getMinimumDuration() == null
                || request.getMinimumDuration() <= 0) {

            throw new InvalidActivityException(
                    "Minimum duration must be greater than 0");
        }

        if (request.getStartDate() == null) {

            throw new InvalidActivityException(
                    "Start date is required");
        }

        if (request.getScheduledDays() == null
                || request.getScheduledDays().isEmpty()) {

            throw new InvalidActivityException(
                    "At least one scheduled day is required");
        }
    }

    // ------------------------------------------------
    // ENTITY -> RESPONSE DTO
    // ------------------------------------------------

    private ActivityResponseDTO mapToResponseDTO(
            Activity activity) {

        ActivityResponseDTO response =
                new ActivityResponseDTO();

        response.setId(
                activity.getId());

        response.setName(
                activity.getName());

        response.setMinimumDuration(
                activity.getMinimumDuration());

        response.setCategory(
                activity.getCategory());

        response.setActive(
                activity.getActive());

        response.setStartDate(
                activity.getStartDate());

        response.setScheduledDays(
                activity.getScheduledDays());

        response.setCreatedAt(
                activity.getCreatedAt());

        response.setUpdatedAt(
                activity.getUpdatedAt());

        return response;
    }
}