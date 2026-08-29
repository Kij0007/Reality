package com.reality.controller;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reality.dto.MonthlyReportResponseDTO;
import com.reality.services.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(
            ReportService reportService) {

        this.reportService =
                reportService;
    }

    @GetMapping(
            "/activity/{activityId}/month/{month}")
    public ResponseEntity<MonthlyReportResponseDTO>
            getMonthlyReport(
                    @PathVariable Long activityId,

                    @PathVariable
                    @DateTimeFormat(pattern = "yyyy-MM")
                    YearMonth month) {

        MonthlyReportResponseDTO response =
                reportService
                        .getMonthlyReport(
                                activityId,
                                month);

        return ResponseEntity.ok(response);
    }
}