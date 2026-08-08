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

/**
 * Passive by design: sends the same plain GET a normal browser visit would
 * (via the shared HttpClient, see ScannerConfig), nothing more. Covers the
 * FR-3.2 "HTTPS usage" row: whether the site is served over HTTPS, and if
 * it's registered as plain HTTP, whether it redirects to HTTPS.
 */
@Component
public class HttpsCheck implements ScanCheck {

    private final HttpClient httpClient;

    public HttpsCheck(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "https-usage";
    }

    @Override
    public CheckResult run(Website website) {
        URI uri = URI.create(website.getUrl());
        boolean isHttps = "https".equalsIgnoreCase(uri.getScheme());

        return isHttps ? checkHttpsReachable(uri) : checkHttpRedirectsToHttps(uri);
    }

    private CheckResult checkHttpsReachable(URI uri) {
        try {
            ScannerSupport.get(httpClient, uri);
            return new CheckResult(getName(), Severity.INFO,
                    "Website is served over HTTPS.",
                    "No action needed.");
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not verify HTTPS connectivity: " + e.getMessage(),
                    "Confirm the site is reachable and re-run the scan.");
        }
    }

    private CheckResult checkHttpRedirectsToHttps(URI uri) {
        try {
            HttpResponse<Void> response = ScannerSupport.get(httpClient, uri);
            int status = response.statusCode();
            String location = response.headers().firstValue("Location").orElse("");

            if (status >= 300 && status < 400 && location.startsWith("https://")) {
                return new CheckResult(getName(), Severity.INFO,
                        "HTTP requests redirect to HTTPS.",
                        "No action needed.");
            }

            return new CheckResult(getName(), Severity.HIGH,
                    "Website is served over HTTP without redirecting to HTTPS.",
                    "Configure the server to redirect all HTTP traffic to HTTPS.");
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not verify HTTPS redirect behavior: " + e.getMessage(),
                    "Confirm the site is reachable and re-run the scan.");
        }
    }
}
