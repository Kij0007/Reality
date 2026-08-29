package com.reality.services;

import com.reality.dto.StreakResponseDTO;

public interface StreakService {

    StreakResponseDTO getCurrentStreak(Long activityId);
}