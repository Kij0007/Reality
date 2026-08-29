package com.reality.services;

import java.time.YearMonth;

import com.reality.dto.MonthlyReportResponseDTO;

public interface ReportService {

    MonthlyReportResponseDTO getMonthlyReport(
            Long activityId,
            YearMonth month);
}