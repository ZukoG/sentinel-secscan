package com.sentinel.secscan.scanner;

import com.sentinel.secscan.domain.Severity;

/**
 * What a single ScanCheck produces. Deliberately not the Finding JPA
 * entity, that requires a Scan to attach to, and no Scan exists yet at
 * check-execution time (Day 12's orchestration creates one and converts
 * these into real Finding rows once it does).
 */
public record CheckResult(String checkName, Severity severity, String description, String recommendation) {
}
