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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Walks the whole redirect chain looking for loops and insecure (HTTP)
 * hops anywhere in it, a different concern from HttpsCheck (Day 7), which
 * only checks whether the first hop upgrades HTTP to HTTPS. A chain that
 * starts secure but dips back to HTTP partway through, or loops, is
 * something HttpsCheck would never see.
 *
 * evaluate() is separated from followRedirects() so the loop/scheme logic
 * is unit testable with a hand-built list of URIs, no real scheme-mixing
 * server needed. The chain-walking mechanics (multi-hop following, loop
 * detection, hop-count cap) are still fully testable against a local
 * plain-HTTP server, since none of that depends on scheme.
 */
@Component
public class RedirectAnalysisCheck implements ScanCheck {

    private static final int MAX_HOPS = 10;

    private final HttpClient httpClient;

    public RedirectAnalysisCheck(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "redirect-analysis";
    }

    @Override
    public CheckResult run(Website website) {
        try {
            List<URI> chain = followRedirects(URI.create(website.getUrl()));
            return evaluate(chain);
        } catch (RedirectChainException e) {
            return new CheckResult(getName(), Severity.HIGH,
                    e.getMessage(),
                    "Fix the server configuration causing the redirect chain problem.");
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not analyze the redirect chain: " + e.getMessage(),
                    "Confirm the site is reachable and re-run the scan.");
        }
    }

    CheckResult evaluate(List<URI> chain) {
        if (chain.size() <= 1) {
            return new CheckResult(getName(), Severity.INFO,
                    "No redirects occurred.",
                    "No action needed.");
        }

        List<URI> insecureHops = chain.stream()
                .filter(uri -> "http".equalsIgnoreCase(uri.getScheme()))
                .toList();

        if (!insecureHops.isEmpty()) {
            return new CheckResult(getName(), Severity.HIGH,
                    "Redirect chain includes an insecure (HTTP) hop: " + describeChain(insecureHops),
                    "Ensure every hop in the redirect chain uses HTTPS.");
        }

        return new CheckResult(getName(), Severity.INFO,
                "Redirect chain uses HTTPS throughout (" + (chain.size() - 1) + " redirect(s)).",
                "No action needed.");
    }

    private List<URI> followRedirects(URI start) throws Exception {
        List<URI> chain = new ArrayList<>();
        Set<URI> visited = new HashSet<>();
        URI current = start;

        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            if (!visited.add(current)) {
                throw new RedirectChainException("Redirect loop detected: " + describeChain(chain));
            }
            chain.add(current);

            HttpResponse<Void> response = ScannerSupport.get(httpClient, current);
            int status = response.statusCode();
            if (status < 300 || status >= 400) {
                return chain;
            }

            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("Redirect response had no Location header"));
            current = current.resolve(location);

            if (hop == MAX_HOPS) {
                throw new RedirectChainException(
                        "Stopped after " + MAX_HOPS + " redirects without reaching a final response, this may indicate a redirect loop.");
            }
        }

        return chain;
    }

    private String describeChain(List<URI> uris) {
        return uris.stream().map(URI::toString).collect(Collectors.joining(" -> "));
    }

    private static final class RedirectChainException extends RuntimeException {
        RedirectChainException(String message) {
            super(message);
        }
    }
}
