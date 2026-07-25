package com.reality.services;

import java.util.List;

import com.reality.dto.ActivityRequestDTO;
import com.reality.dto.ActivityResponseDTO;

public interface ActivityService {

    ActivityResponseDTO createActivity(ActivityRequestDTO request);
    
    List<ActivityResponseDTO> getAllActivities();

    ActivityResponseDTO getActivityById(Long id);

    ActivityResponseDTO updateActivity(Long id, ActivityRequestDTO request);

    void deleteActivity(Long id);

}