package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Role;
import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mostly tests evaluate() directly, the attribute-parsing logic doesn't
 * need a real connection at all. One run() test confirms Set-Cookie
 * headers are actually extracted correctly from a real response.
 */
class CookieSecurityCheckTest {

    private final CookieSecurityCheck check = new CookieSecurityCheck(HttpClient.newHttpClient());

    @Test
    void noCookiesIsInfo() {
        CheckResult result = check.evaluate(List.of());

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void fullyCompliantCookieIsInfo() {
        CheckResult result = check.evaluate(List.of("session=abc123; Secure; HttpOnly; SameSite=Strict"));

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void missingSecureIsFlaggedAsMedium() {
        CheckResult result = check.evaluate(List.of("session=abc123; HttpOnly; SameSite=Strict"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.description()).contains("Secure");
    }

    @Test
    void sameSiteNoneWithoutSecureEscalatesToHigh() {
        // Missing Secure, missing HttpOnly, and SameSite=None without
        // Secure, three distinct issues from one cookie.
        CheckResult result = check.evaluate(List.of("tracking=xyz; SameSite=None"));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.description()).contains("browsers will reject");
    }

    @Test
    void multipleCookiesWithIssuesEscalateToHigh() {
        CheckResult result = check.evaluate(List.of(
                "a=1",
                "b=2; Secure"));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void runExtractsCookiesFromRealResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Secure; HttpOnly; SameSite=Strict");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        try {
            User owner = new User("owner@example.com", "hash", Role.USER);
            Website website = new Website(owner, "http://localhost:" + server.getAddress().getPort());

            CheckResult result = check.run(website);

            assertThat(result.severity()).isEqualTo(Severity.INFO);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void flagsUnreachableSiteAsMedium() {
        User owner = new User("owner@example.com", "hash", Role.USER);
        CheckResult result = check.run(new Website(owner, "http://localhost:1"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }
}
