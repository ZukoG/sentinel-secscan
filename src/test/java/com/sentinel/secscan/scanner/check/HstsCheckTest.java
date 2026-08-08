package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.scanner.CheckResult;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests evaluate() directly rather than run(), no real HTTPS connection
 * needed to verify the actual decision logic (missing header, missing or
 * short max-age).
 */
class HstsCheckTest {

    private final HstsCheck check = new HstsCheck(HttpClient.newHttpClient());

    @Test
    void flagsMissingHeaderAsHigh() {
        CheckResult result = check.evaluate(null);

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void flagsMissingMaxAgeAsMedium() {
        CheckResult result = check.evaluate("includeSubDomains");

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void flagsZeroMaxAgeAsMedium() {
        CheckResult result = check.evaluate("max-age=0");

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void flagsShortMaxAgeAsLow() {
        CheckResult result = check.evaluate("max-age=3600");

        assertThat(result.severity()).isEqualTo(Severity.LOW);
    }

    @Test
    void acceptsLongMaxAgeAsInfo() {
        CheckResult result = check.evaluate("max-age=31536000; includeSubDomains; preload");

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void treatsMalformedMaxAgeAsMissing() {
        CheckResult result = check.evaluate("max-age=notanumber");

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }
}
