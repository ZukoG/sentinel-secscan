package com.sentinel.secscan.scan.dto;

import com.sentinel.secscan.domain.RiskRating;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanStatus;

import java.time.Instant;

/**
 * Lightweight compared to ScanResponse: no findings included. This is
 * for listing many scans at once (history/trend, Day 14), not viewing
 * one scan in detail.
 */
public record ScanSummaryResponse(
        Long id,
        ScanStatus status,
        Integer overallScore,
        RiskRating riskRating,
        Instant startedAt,
        Instant completedAt
) {

    public static ScanSummaryResponse from(Scan scan) {
        return new ScanSummaryResponse(
                scan.getId(),
                scan.getStatus(),
                scan.getOverallScore(),
                scan.getRiskRating(),
                scan.getStartedAt(),
                scan.getCompletedAt());
    }
}
