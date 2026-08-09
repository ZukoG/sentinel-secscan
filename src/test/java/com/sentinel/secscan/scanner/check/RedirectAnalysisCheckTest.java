package com.sentinel.secscan.scanner.check;

import com.sentinel.secscan.domain.Role;
import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * evaluate() covers the loop/scheme logic with hand-built URI lists, no
 * server needed. The other tests exercise followRedirects() (the actual
 * hop-by-hop walking) against a local plain-HTTP server, loop detection
 * and the hop-count cap don't depend on scheme, so a local server proves
 * the mechanics work even though every hop in these tests is HTTP.
 */
class RedirectAnalysisCheckTest {

    private final RedirectAnalysisCheck check = new RedirectAnalysisCheck(HttpClient.newHttpClient());

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void noRedirectsIsInfo() {
        CheckResult result = check.evaluate(List.of(URI.create("https://example.com")));

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void allHttpsChainIsInfo() {
        CheckResult result = check.evaluate(List.of(
                URI.create("https://example.com"),
                URI.create("https://www.example.com")));

        assertThat(result.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void insecureHopIsFlaggedAsHigh() {
        CheckResult result = check.evaluate(List.of(
                URI.create("http://example.com"),
                URI.create("https://example.com")));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void followsAMultiHopChainToItsFinalDestination() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/start", exchange -> redirectTo(exchange, "/middle"));
        server.createContext("/middle", exchange -> redirectTo(exchange, "/end"));
        server.createContext("/end", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        // Both hops are plain HTTP, this is a local test server, so the
        // result is correctly HIGH, that's evaluate()'s job, tested
        // separately above. This test is about the chain-walking mechanics.
        CheckResult result = check.run(websiteAt("/start"));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.description()).contains("/end");
    }

    @Test
    void detectsARedirectLoop() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/a", exchange -> redirectTo(exchange, "/b"));
        server.createContext("/b", exchange -> redirectTo(exchange, "/a"));
        server.start();

        CheckResult result = check.run(websiteAt("/a"));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.description()).contains("loop");
    }

    @Test
    void stopsAfterTooManyRedirects() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        for (int i = 0; i < 20; i++) {
            int next = i + 1;
            server.createContext("/" + i, exchange -> redirectTo(exchange, "/" + next));
        }
        server.start();

        CheckResult result = check.run(websiteAt("/0"));

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.description()).contains("Stopped after");
    }

    @Test
    void flagsRedirectWithNoLocationHeaderAsMedium() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        CheckResult result = check.run(websiteAt("/"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void flagsUnreachableSiteAsMedium() {
        CheckResult result = check.run(websiteAtRaw("http://localhost:1"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    private void redirectTo(com.sun.net.httpserver.HttpExchange exchange, String path) throws java.io.IOException {
        exchange.getResponseHeaders().add("Location", path);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private Website websiteAt(String path) {
        return websiteAtRaw("http://localhost:" + server.getAddress().getPort() + path);
    }

    private Website websiteAtRaw(String url) {
        User owner = new User("owner@example.com", "hash", Role.USER);
        return new Website(owner, url);
    }
}
