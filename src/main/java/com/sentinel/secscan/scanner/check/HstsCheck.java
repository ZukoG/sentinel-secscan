package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sentinel.secscan.scanner.ScanCheck;
import com.sentinel.secscan.scanner.ScannerSupport;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Locale;

/**
 * HSTS only means anything over HTTPS (browsers ignore the header on
 * plain HTTP responses), so this always tests the HTTPS form of the
 * registered URL. Whether HTTPS is used/redirected to at all is
 * HttpsCheck's job (Day 7), this only runs once that's established.
 *
 * evaluate() is separated from run() so the actual decision logic (missing
 * header, missing/short max-age) can be unit tested directly without a
 * real HTTPS connection, no self-signed-cert test server needed.
 */
@Component
public class HstsCheck implements ScanCheck {

    // A commonly cited minimum for a meaningfully effective HSTS policy.
    // The widely recommended best practice is 31536000 (one year), that's
    // in the recommendation text rather than enforced as a hard minimum.
    private static final long RECOMMENDED_MIN_MAX_AGE_SECONDS = 15_768_000L;

    private final HttpClient httpClient;

    public HstsCheck(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "hsts";
    }

    @Override
    public CheckResult run(Website website) {
        try {
            URI httpsUri = ScannerSupport.toHttpsUri(website.getUrl());
            HttpResponse<Void> response = ScannerSupport.get(httpClient, httpsUri);
            String header = response.headers().firstValue("Strict-Transport-Security").orElse(null);
            return evaluate(header);
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not verify HSTS configuration: " + e.getMessage(),
                    "Confirm the site is reachable over HTTPS and re-run the scan.");
        }
    }

    CheckResult evaluate(String headerValue) {
        if (headerValue == null) {
            return new CheckResult(getName(), Severity.HIGH,
                    "Strict-Transport-Security header is missing.",
                    "Add a Strict-Transport-Security header with a max-age of at least one year.");
        }

        long maxAge = extractMaxAge(headerValue);
        if (maxAge <= 0) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Strict-Transport-Security header is present but has no valid max-age.",
                    "Set max-age to at least 31536000 (one year).");
        }
        if (maxAge < RECOMMENDED_MIN_MAX_AGE_SECONDS) {
            return new CheckResult(getName(), Severity.LOW,
                    "Strict-Transport-Security max-age (" + maxAge + "s) is shorter than the recommended minimum.",
                    "Increase max-age to at least 31536000 (one year).");
        }

        return new CheckResult(getName(), Severity.INFO,
                "Strict-Transport-Security is present with a max-age of " + maxAge + " seconds.",
                "No action needed.");
    }

    private long extractMaxAge(String headerValue) {
        for (String directive : headerValue.split(";")) {
            String trimmed = directive.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("max-age=")) {
                try {
                    return Long.parseLong(trimmed.substring("max-age=".length()).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
