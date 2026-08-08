package com.sentinel.secscan.scanner;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Small helpers shared by every ScanCheck that talks HTTP, pulled out
 * once HttpsCheck's private send() logic started getting duplicated
 * across the checks added on Day 8 (SecurityHeadersCheck, HstsCheck,
 * SslCertificateCheck).
 */
public final class ScannerSupport {

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private ScannerSupport() {
    }

    /**
     * HSTS and certificate inspection are only meaningful over HTTPS, so
     * checks that need it derive the HTTPS form of a website's registered
     * URL regardless of which scheme it was actually registered with.
     */
    public static URI toHttpsUri(String url) {
        URI original = URI.create(url);
        if ("https".equalsIgnoreCase(original.getScheme())) {
            return original;
        }
        try {
            return new URI("https", original.getAuthority(), original.getPath(), original.getQuery(), original.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot derive an HTTPS URL from " + url, e);
        }
    }

    /** Discards the body, checks only ever inspect status/headers/TLS session. */
    public static HttpResponse<Void> get(HttpClient httpClient, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
