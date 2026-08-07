package com.sentinel.secscan.scanner;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.Website;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs every registered ScanCheck against a website. Spring collects all
 * ScanCheck beans into the injected list automatically, so adding a new
 * check (Days 8-9) never requires touching this class, the whole point of
 * the Strategy pattern here.
 *
 * Each check runs inside its own try/catch: one check throwing must not
 * abort the others (NFR-6, FR-3.4 in docs/SRS.md). This is enforced here
 * rather than trusting every individual check to handle its own errors,
 * so it protects checks added later too.
 */
@Component
public class ScannerEngine {

    private final List<ScanCheck> checks;

    public ScannerEngine(List<ScanCheck> checks) {
        this.checks = checks;
    }

    public List<CheckResult> runAll(Website website) {
        return checks.stream()
                .map(check -> runSafely(check, website))
                .toList();
    }

    private CheckResult runSafely(ScanCheck check, Website website) {
        try {
            return check.run(website);
        } catch (Exception e) {
            return new CheckResult(
                    check.getName(),
                    Severity.MEDIUM,
                    "Check failed unexpectedly: " + e.getMessage(),
                    "Investigate why this check could not complete and re-run the scan."
            );
        }
    }
}
