package com.sentinel.secscan.scan.dto;

import java.util.List;

/**
 * completedScans is chronological (oldest to newest), the natural order
 * for plotting a trend line. scoreDelta and direction compare only the
 * two most recent completed scans; direction is INSUFFICIENT_DATA
 * (scoreDelta null) when fewer than two exist yet.
 */
public record TrendResponse(
        List<ScanSummaryResponse> completedScans,
        Integer scoreDelta,
        TrendDirection direction
) {
}
