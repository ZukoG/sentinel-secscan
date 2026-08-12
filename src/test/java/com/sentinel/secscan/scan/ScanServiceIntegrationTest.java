package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.FindingRepository;
import com.sentinel.secscan.domain.Role;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanRepository;
import com.sentinel.secscan.domain.ScanStatus;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.UserRepository;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.domain.WebsiteRepository;
import com.sentinel.secscan.scan.dto.ScanResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full Spring context and a real Postgres (same requirement as
 * SentinelSecscanApplicationTests), needed because @Async only actually
 * runs asynchronously through Spring's real proxy machinery, not a mock.
 * Tests the service layer directly rather than through MockMvc/HTTP,
 * ScanController is thin pass-through, the orchestration logic under
 * test lives in ScanService/ScanRunner. The real HTTP endpoints were
 * additionally verified manually via docker compose, see the PR.
 *
 * Points at a local HttpServer, not a real external site, so results are
 * deterministic: every check either produces a predictable finding
 * against the controlled response, or falls into its own "could not
 * verify" path (SslCertificateCheck/HstsCheck, since this test server
 * only speaks plain HTTP). Assertions focus on orchestration correctness
 * (did all six checks run, get persisted, and get scored), not on each
 * check's exact severity, that's already covered by their own tests.
 */
@SpringBootTest
class ScanServiceIntegrationTest {

    @Autowired
    private ScanService scanService;
    @Autowired
    private ScanRepository scanRepository;
    @Autowired
    private FindingRepository findingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WebsiteRepository websiteRepository;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void startScanRunsAllChecksAndPersistsResults() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        // A fixed email would collide on a re-run against the same
        // database (caught by actually running the full suite twice,
        // not just this test in isolation): the standalone run leaves
        // the row behind, so the next full-suite run hits the unique
        // constraint. A random email per run avoids that regardless of
        // what's already in the database.
        User owner = userRepository.save(new User("scan-it-" + UUID.randomUUID() + "@example.com", "hash", Role.USER));
        Website website = websiteRepository.save(
                new Website(owner, "http://localhost:" + server.getAddress().getPort()));

        ScanResponse triggered = scanService.startScan(owner, website.getId());
        assertThat(triggered.status()).isEqualTo(ScanStatus.IN_PROGRESS);

        Scan completed = waitForCompletion(triggered.id(), Duration.ofSeconds(20));

        assertThat(completed.getStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(completed.getOverallScore()).isBetween(0, 100);
        assertThat(completed.getRiskRating()).isNotNull();
        assertThat(completed.getCompletedAt()).isAfterOrEqualTo(completed.getStartedAt());
        assertThat(findingRepository.findByScanId(completed.getId())).hasSize(6);
    }

    private Scan waitForCompletion(Long scanId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Scan scan = scanRepository.findById(scanId).orElseThrow();
            if (scan.getStatus() != ScanStatus.IN_PROGRESS) {
                return scan;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Scan " + scanId + " did not complete within " + timeout);
    }
}
