package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sentinel.secscan.scanner.ScanCheck;
import com.sentinel.secscan.scanner.ScannerSupport;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

/**
 * Reads the certificate the server presented during the TLS handshake the
 * shared HttpClient already performed (HttpResponse.sslSession()), no
 * separate certificate library needed. A failed handshake (untrusted,
 * self-signed, hostname mismatch) is caught explicitly, that's arguably
 * the most important thing this check can catch, an invalid chain never
 * reaches the point of having an inspectable expiry date.
 *
 * evaluate() is separated from run() so the expiry/validity decision logic
 * can be unit tested with a mocked certificate, no real TLS handshake or
 * generated test certificate needed.
 */
@Component
public class SslCertificateCheck implements ScanCheck {

    private static final int EXPIRY_HIGH_THRESHOLD_DAYS = 14;
    private static final int EXPIRY_MEDIUM_THRESHOLD_DAYS = 30;

    private final HttpClient httpClient;

    public SslCertificateCheck(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "ssl-certificate";
    }

    @Override
    public CheckResult run(Website website) {
        try {
            URI httpsUri = ScannerSupport.toHttpsUri(website.getUrl());
            HttpResponse<Void> response = ScannerSupport.get(httpClient, httpsUri);
            SSLSession session = response.sslSession()
                    .orElseThrow(() -> new IllegalStateException("No TLS session for an HTTPS response"));

            Certificate[] chain = session.getPeerCertificates();
            if (chain.length == 0 || !(chain[0] instanceof X509Certificate leaf)) {
                return new CheckResult(getName(), Severity.MEDIUM,
                        "Server did not present an inspectable certificate.",
                        "Confirm the site serves a standard X.509 certificate and re-run the scan.");
            }

            return evaluate(leaf);
        } catch (SSLHandshakeException e) {
            return new CheckResult(getName(), Severity.HIGH,
                    "TLS handshake failed, the certificate may be invalid, self-signed, or mismatched: " + e.getMessage(),
                    "Install a valid certificate from a trusted certificate authority for this hostname.");
        } catch (Exception e) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Could not verify the SSL certificate: " + e.getMessage(),
                    "Confirm the site is reachable over HTTPS and re-run the scan.");
        }
    }

    CheckResult evaluate(X509Certificate certificate) {
        Instant now = Instant.now();
        Instant notBefore = certificate.getNotBefore().toInstant();
        Instant notAfter = certificate.getNotAfter().toInstant();
        String issuer = certificate.getIssuerX500Principal().getName();

        if (now.isBefore(notBefore)) {
            return new CheckResult(getName(), Severity.HIGH,
                    "Certificate is not yet valid (valid from " + notBefore + ").",
                    "Confirm the server's certificate and clock are configured correctly.");
        }

        if (now.isAfter(notAfter)) {
            return new CheckResult(getName(), Severity.CRITICAL,
                    "Certificate has expired (expired on " + notAfter + "), issued by " + issuer + ".",
                    "Renew the certificate immediately.");
        }

        long daysUntilExpiry = Duration.between(now, notAfter).toDays();
        if (daysUntilExpiry <= EXPIRY_HIGH_THRESHOLD_DAYS) {
            return new CheckResult(getName(), Severity.HIGH,
                    "Certificate expires in " + daysUntilExpiry + " day(s), issued by " + issuer + ".",
                    "Renew the certificate as soon as possible.");
        }
        if (daysUntilExpiry <= EXPIRY_MEDIUM_THRESHOLD_DAYS) {
            return new CheckResult(getName(), Severity.MEDIUM,
                    "Certificate expires in " + daysUntilExpiry + " day(s), issued by " + issuer + ".",
                    "Plan to renew the certificate soon.");
        }

        return new CheckResult(getName(), Severity.INFO,
                "Certificate is valid until " + notAfter + ", issued by " + issuer + ".",
                "No action needed.");
    }
}
