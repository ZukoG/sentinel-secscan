package com.sentinel.secscan.domain;

/**
 * Matches docs/SRS.md FR-5.2 and the scan lifecycle sequence diagram: a
 * scan row is created already IN_PROGRESS when triggered, then moves to
 * COMPLETED or FAILED once the scanner engine finishes (Day 12).
 */
public enum ScanStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
