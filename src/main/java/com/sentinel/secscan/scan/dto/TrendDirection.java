package com.sentinel.secscan.scan.dto;

/**
 * Derived purely from comparing the two most recent COMPLETED scans'
 * scores, never persisted, that's why this lives with the DTOs rather
 * than in the domain package alongside the entity-backed enums.
 */
public enum TrendDirection {
    IMPROVED,
    WORSENED,
    UNCHANGED,
    INSUFFICIENT_DATA
}
