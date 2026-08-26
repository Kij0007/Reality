package com.reality.services;

import java.time.LocalDate;

import com.reality.dto.DailyProgressResponseDTO;

public interface DailyProgressService {

    DailyProgressResponseDTO getDailyProgress(
            Long activityId,
            LocalDate date);
}