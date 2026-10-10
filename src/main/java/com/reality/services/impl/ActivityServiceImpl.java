package com.reality.services.impl;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reality.dto.ActivityRequestDTO;
import com.reality.dto.ActivityResponseDTO;
import com.reality.entity.Activity;
import com.reality.exception.InvalidActivityException;
import com.reality.repository.ActivityRepository;
import com.reality.repository.UserAccountRepository;
import com.reality.services.ActivityService;
import com.reality.services.OwnedResources;

@Service
@Transactional(readOnly = true)
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository activityRepository;
    private final UserAccountRepository users;
    private final OwnedResources owned;

    public ActivityServiceImpl(ActivityRepository activityRepository, UserAccountRepository users, OwnedResources owned) {
        this.activityRepository = activityRepository;
        this.users = users;
        this.owned = owned;
    }

    // ------------------------------------------------
    // CREATE ACTIVITY
    // ------------------------------------------------

    @Override
    @Transactional
    public ActivityResponseDTO createActivity(
            ActivityRequestDTO request) {

        validateActivityRequest(request);

        Activity activity = new Activity();
        activity.setOwner(users.getReferenceById(owned.ownerId()));

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
                activityRepository.findByOwnerIdAndActiveTrue(owned.ownerId());

        return activities.stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    // ------------------------------------------------
    // GET ACTIVITY BY ID
    // ------------------------------------------------

    @Override
    public ActivityResponseDTO getActivityById(Long id) {

        Activity activity = owned.activeActivity(id);

        return mapToResponseDTO(activity);
    }

    // ------------------------------------------------
    // UPDATE ACTIVITY
    // ------------------------------------------------

    @Override
    @Transactional
    public ActivityResponseDTO updateActivity(
            Long id,
            ActivityRequestDTO request) {

        Activity activity = owned.activeActivity(id);
        validateActivityRequest(request);

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
    @Transactional
    public void deleteActivity(Long id) {

        Activity activity = owned.activeActivity(id);

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
                new HashSet<>(activity.getScheduledDays()));

        response.setCreatedAt(
                activity.getCreatedAt());

        response.setUpdatedAt(
                activity.getUpdatedAt());

        return response;
    }
}
