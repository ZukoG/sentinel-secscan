package com.sentinel.secscan.domain;

/**
 * Standard five-level severity scale for a Finding, similar to how most
 * vulnerability scanners bucket results. Feeds into the scoring engine's
 * weights, which is one of the open questions in docs/SRS.md (Day 11).
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
