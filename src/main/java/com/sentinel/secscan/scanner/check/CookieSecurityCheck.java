package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sentinel.secscan.scanner.ScanCheck;
import com.sentinel.secscan.scanner.ScannerSupport;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks the Secure, HttpOnly, and SameSite attributes FR-3.2 names, on
 * every cookie the site sets. evaluate() is separated from run() so the
 * attribute-parsing logic is unit testable without a server.
 */
@Component
public class CookieSecurityCheck implements ScanCheck {

    private final HttpClient httpClient;

    public CookieSecurityCheck(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "cookie-security";
    }

    @Override
    public CheckResult run(Website website) {
        try {
            URI uri = URI.create(website.getUrl());
            List<String> setCookieHeaders = ScannerSupport.get(httpClient, uri).headers().allValues("Set-Cookie");
            return evaluate(setCookieHeaders);
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not verify cookie security: " + e.getMessage(),
                    "Confirm the site is reachable and re-run the scan.");
        }
    }

    CheckResult evaluate(List<String> setCookieHeaders) {
        if (setCookieHeaders.isEmpty()) {
            return new CheckResult(getName(), Severity.INFO,
                    "The site does not set any cookies.",
                    "No action needed.");
        }

        List<String> issues = new ArrayList<>();
        for (String header : setCookieHeaders) {
            issues.addAll(findIssues(header));
        }

        if (issues.isEmpty()) {
            return new CheckResult(getName(), Severity.INFO,
                    "All cookies set the Secure, HttpOnly, and SameSite attributes correctly.",
                    "No action needed.");
        }

        Severity severity = issues.size() >= 3 ? Severity.HIGH : Severity.MEDIUM;
        return new CheckResult(getName(), severity,
                String.join(" ", issues),
                "Add the missing cookie attributes listed above.");
    }

    private List<String> findIssues(String setCookieHeader) {
        List<String> issues = new ArrayList<>();
        String cookieName = setCookieHeader.split("=", 2)[0].trim();
        List<String> attributes = List.of(setCookieHeader.split(";"));

        boolean secure = attributes.stream().anyMatch(a -> a.trim().equalsIgnoreCase("Secure"));
        boolean httpOnly = attributes.stream().anyMatch(a -> a.trim().equalsIgnoreCase("HttpOnly"));
        String sameSite = attributes.stream()
                .map(String::trim)
                .filter(a -> a.toLowerCase(Locale.ROOT).startsWith("samesite="))
                .map(a -> a.substring("samesite=".length()))
                .findFirst()
                .orElse(null);

        if (!secure) {
            issues.add("Cookie '" + cookieName + "' is missing the Secure attribute.");
        }
        if (!httpOnly) {
            issues.add("Cookie '" + cookieName + "' is missing the HttpOnly attribute.");
        }
        if (sameSite == null) {
            issues.add("Cookie '" + cookieName + "' does not set a SameSite attribute.");
        } else if (sameSite.equalsIgnoreCase("None") && !secure) {
            // Spec-invalid: browsers reject SameSite=None cookies that aren't
            // also Secure, worth its own distinct message, not just "missing
            // an attribute".
            issues.add("Cookie '" + cookieName + "' sets SameSite=None without Secure, browsers will reject this cookie.");
        }

        return issues;
    }
}
