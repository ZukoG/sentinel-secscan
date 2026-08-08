package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.scanner.CheckResult;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests evaluate() directly with a mocked certificate rather than run(),
 * a real TLS handshake or a generated test certificate isn't needed to
 * verify the actual expiry/validity decision logic.
 */
class SslCertificateCheckTest {

    private final SslCertificateCheck check = new SslCertificateCheck(HttpClient.newHttpClient());

    @Test
    void flagsNotYetValidCertificateAsHigh() {
        X509Certificate cert = certificateValidFor(Duration.ofDays(1), Duration.ofDays(365));

        CheckResult result = check.evaluate(cert);

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void flagsExpiredCertificateAsCritical() {
        X509Certificate cert = certificateValidFor(Duration.ofDays(-400), Duration.ofDays(-1));

        CheckResult result = check.evaluate(cert);

        assertThat(result.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void flagsExpiringSoonAsHigh() {
        X509Certificate cert = certificateValidFor(Duration.ofDays(-300), Duration.ofDays(5));

        CheckResult result = check.evaluate(cert);

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void flagsExpiringWithinAMonthAsMedium() {
        X509Certificate cert = certificateValidFor(Duration.ofDays(-300), Duration.ofDays(20));

        CheckResult result = check.evaluate(cert);

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void acceptsHealthyCertificateAsInfo() {
        X509Certificate cert = certificateValidFor(Duration.ofDays(-30), Duration.ofDays(300));

        CheckResult result = check.evaluate(cert);

        assertThat(result.severity()).isEqualTo(Severity.INFO);
        assertThat(result.description()).contains("Test CA");
    }

    private X509Certificate certificateValidFor(Duration fromNow, Duration toNow) {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotBefore()).thenReturn(Date.from(Instant.now().plus(fromNow)));
        when(cert.getNotAfter()).thenReturn(Date.from(Instant.now().plus(toNow)));
        when(cert.getIssuerX500Principal()).thenReturn(new X500Principal("CN=Test CA"));
        return cert;
    }
}
