package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Role;
import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No TLS involved (headers are checked over plain HTTP here), so a local
 * HttpServer covers this fully, unlike HstsCheck/SslCertificateCheck.
 */
class SecurityHeadersCheckTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final SecurityHeadersCheck check = new SecurityHeadersCheck(httpClient);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void passesWhenAllHeadersPresentAndCorrect() throws Exception {
        server = startServer(exchange -> {
            exchange.getResponseHeaders().add("Content-Security-Policy", "default-src 'self'");
            exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().add("X-Frame-Options", "DENY");
            exchange.getResponseHeaders().add("Referrer-Policy", "no-referrer");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        CheckResult result = check.run(websiteAt(server));

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void flagsAllHeadersMissingAsHigh() throws Exception {
        server = startServer(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        CheckResult result = check.run(websiteAt(server));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void flagsInvalidFrameOptionsAsMedium() throws Exception {
        server = startServer(exchange -> {
            exchange.getResponseHeaders().add("Content-Security-Policy", "default-src 'self'");
            exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().add("X-Frame-Options", "ALLOW-FROM https://example.com");
            exchange.getResponseHeaders().add("Referrer-Policy", "no-referrer");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        CheckResult result = check.run(websiteAt(server));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.description()).contains("X-Frame-Options");
    }

    @Test
    void flagsUnreachableSiteAsMedium() {
        User owner = new User("owner@example.com", "hash", Role.USER);
        CheckResult result = check.run(new Website(owner, "http://localhost:1"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    private HttpServer startServer(HttpHandler handler) throws Exception {
        HttpServer newServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        newServer.createContext("/", handler);
        newServer.start();
        return newServer;
    }

    private Website websiteAt(HttpServer server) {
        User owner = new User("owner@example.com", "hash", Role.USER);
        return new Website(owner, "http://localhost:" + server.getAddress().getPort());
    }
}
