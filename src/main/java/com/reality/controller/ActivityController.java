package com.reality.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reality.dto.ActivityRequestDTO;
import com.reality.dto.ActivityResponseDTO;
import com.reality.services.ActivityService;

@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    // Create Activity
    @PostMapping
    public ResponseEntity<ActivityResponseDTO> createActivity(
            @RequestBody ActivityRequestDTO request) {

        ActivityResponseDTO response = activityService.createActivity(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Get All Activities
    @GetMapping
    public ResponseEntity<List<ActivityResponseDTO>> getAllActivities() {

        List<ActivityResponseDTO> activities = activityService.getAllActivities();

        return ResponseEntity.ok(activities);
    }

    // Get Activity By Id
    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponseDTO> getActivityById(
            @PathVariable Long id) {

        ActivityResponseDTO activity = activityService.getActivityById(id);

        return ResponseEntity.ok(activity);
    }

    // Update Activity
    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponseDTO> updateActivity(
            @PathVariable Long id,
            @RequestBody ActivityRequestDTO request) {

        ActivityResponseDTO updatedActivity =
                activityService.updateActivity(id, request);

        return ResponseEntity.ok(updatedActivity);
    }

    // Soft Delete Activity
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteActivity(
            @PathVariable Long id) {

        activityService.deleteActivity(id);

        return ResponseEntity.ok("Activity deleted successfully.");
    }
}