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
 * No REST endpoint exists yet to exercise this through (that's Day 12's
 * scan orchestration), so unlike every other day so far, this is tested
 * directly. A local HttpServer (JDK built-in, no new dependency) covers
 * both http:// scheme branches deterministically, no real network needed.
 * The "https reachable" happy path isn't automated here, that would need a
 * self-signed cert test harness, disproportionate for a skeleton check,
 * it was checked manually against a real site instead. The "https
 * unreachable" branch is covered, using a closed local port.
 */
class HttpsCheckTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final HttpsCheck check = new HttpsCheck(httpClient);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void flagsPlainHttpWithNoRedirect() throws Exception {
        server = startServer(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        CheckResult result = check.run(websiteAt("http://localhost:" + server.getAddress().getPort()));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void acceptsHttpThatRedirectsToHttps() throws Exception {
        server = startServer(exchange -> {
            exchange.getResponseHeaders().add("Location", "https://example.com/");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        CheckResult result = check.run(websiteAt("http://localhost:" + server.getAddress().getPort()));

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void flagsUnreachableHttps() {
        CheckResult result = check.run(websiteAt("https://localhost:1"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    private HttpServer startServer(HttpHandler handler) throws Exception {
        HttpServer newServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        newServer.createContext("/", handler);
        newServer.start();
        return newServer;
    }

    private Website websiteAt(String url) {
        User owner = new User("owner@example.com", "hash", Role.USER);
        return new Website(owner, url);
    }
}
