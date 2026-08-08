package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sentinel.secscan.scanner.ScanCheck;
import com.sentinel.secscan.scanner.ScannerSupport;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks the four headers FR-3.2 names explicitly. X-Content-Type-Options
 * and X-Frame-Options have a small enough set of valid values to check
 * correctness, not just presence. Content-Security-Policy and
 * Referrer-Policy are presence-only, full policy-syntax validation for
 * either is a much deeper rabbit hole than a passive check needs.
 */
@Component
public class SecurityHeadersCheck implements ScanCheck {

    private static final Set<String> VALID_FRAME_OPTIONS = Set.of("DENY", "SAMEORIGIN");

    private final HttpClient httpClient;

    public SecurityHeadersCheck(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "security-headers";
    }

    @Override
    public CheckResult run(Website website) {
        try {
            URI uri = URI.create(website.getUrl());
            HttpHeaders headers = ScannerSupport.get(httpClient, uri).headers();
            List<String> issues = findIssues(headers);

            if (issues.isEmpty()) {
                return new CheckResult(getName(), Severity.INFO,
                        "All expected security headers are present and correctly configured.",
                        "No action needed.");
            }

            Severity severity = issues.size() >= 3 ? Severity.HIGH : Severity.MEDIUM;
            return new CheckResult(getName(), severity,
                    String.join(" ", issues),
                    "Add or correct the missing/misconfigured security headers listed above.");
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not verify security headers: " + e.getMessage(),
                    "Confirm the site is reachable and re-run the scan.");
        }
    }

    private List<String> findIssues(HttpHeaders headers) {
        List<String> issues = new ArrayList<>();

        if (headers.firstValue("Content-Security-Policy").filter(v -> !v.isBlank()).isEmpty()) {
            issues.add("Content-Security-Policy is missing.");
        }

        String contentTypeOptions = headers.firstValue("X-Content-Type-Options").orElse("");
        if (!contentTypeOptions.equalsIgnoreCase("nosniff")) {
            issues.add("X-Content-Type-Options should be set to 'nosniff'.");
        }

        String frameOptions = headers.firstValue("X-Frame-Options").orElse("").toUpperCase(Locale.ROOT);
        if (!VALID_FRAME_OPTIONS.contains(frameOptions)) {
            issues.add("X-Frame-Options should be 'DENY' or 'SAMEORIGIN'.");
        }

        if (headers.firstValue("Referrer-Policy").filter(v -> !v.isBlank()).isEmpty()) {
            issues.add("Referrer-Policy is missing.");
        }

        return issues;
    }
}
