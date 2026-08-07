package com.sentinel.secscan.scanner;

import com.sentinel.secscan.domain.Website;

/**
 * One passive assessment rule. New checks just implement this and register
 * as a Spring bean (@Component), ScannerEngine picks them up automatically
 * via constructor-injected List<ScanCheck>, no changes needed there.
 */
public interface ScanCheck {

    /**
     * Short, stable, kebab-case identifier (e.g. "https-usage"). Stored as
     * Finding.checkName, so it shouldn't change once a check has run for
     * real, that would orphan historical findings from their check.
     */
    String getName();

    CheckResult run(Website website);
}
