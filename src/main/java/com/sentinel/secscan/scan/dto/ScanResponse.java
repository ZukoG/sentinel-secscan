package com.sentinel.secscan.scan.dto;

import com.sentinel.secscan.domain.Finding;
import com.sentinel.secscan.domain.RiskRating;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanStatus;

import java.time.Instant;
import java.util.List;

/**
 * Findings are included inline, not just the score summary the sequence
 * diagram shows in the trigger response. A real frontend needs this JSON
 * to render results; Day 13's PDF is a different way to consume the same
 * data, not the only way.
 */
public record ScanResponse(
        Long id,
        Long websiteId,
        ScanStatus status,
        Integer overallScore,
        RiskRating riskRating,
        Instant startedAt,
        Instant completedAt,
        List<FindingResponse> findings
) {

    public static ScanResponse from(Scan scan, List<Finding> findings) {
        return new ScanResponse(
                scan.getId(),
                scan.getWebsite().getId(),
                scan.getStatus(),
                scan.getOverallScore(),
                scan.getRiskRating(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                findings.stream().map(FindingResponse::from).toList());
    }
}
