package com.sentinel.secscan.domain;

/**
 * Maps a scan's overall score to a human-facing risk level. The actual
 * score bands live in ScoringService, not here, this enum is just the set
 * of possible outcomes (matches docs/SRS.md FR-4.2's Low/Medium/High/
 * Critical example).
 */
public enum RiskRating {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
