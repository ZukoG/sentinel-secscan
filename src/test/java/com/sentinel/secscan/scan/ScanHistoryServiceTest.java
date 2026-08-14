package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.RiskRating;
import com.sentinel.secscan.domain.Role;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanRepository;
import com.sentinel.secscan.domain.ScanStatus;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scan.dto.ScanSummaryResponse;
import com.sentinel.secscan.scan.dto.TrendDirection;
import com.sentinel.secscan.scan.dto.TrendResponse;
import com.sentinel.secscan.website.WebsiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The history/trend logic (filtering to completed, sorting, delta and
 * direction) is pure once you have the scan list, so this mocks
 * ScanRepository/WebsiteService rather than needing a real database,
 * same approach as ScoringServiceTest.
 */
class ScanHistoryServiceTest {

    private final ScanRepository scanRepository = mock(ScanRepository.class);
    private final WebsiteService websiteService = mock(WebsiteService.class);
    private final ScanHistoryService service = new ScanHistoryService(scanRepository, websiteService);

    private final User owner = new User("owner@example.com", "hash", Role.USER);
    private final Website website = new Website(owner, "https://example.com");

    @BeforeEach
    void stubWebsiteLookup() {
        when(websiteService.getEntityForOwner(owner, 1L)).thenReturn(website);
    }

    @Test
    void getHistoryReturnsEveryScanRegardlessOfStatus() {
        Scan inProgress = scanAt(Instant.now(), null, null);
        Scan completed = completedScanAt(Instant.now().minus(1, ChronoUnit.DAYS), 80);
        when(scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()))
                .thenReturn(List.of(inProgress, completed));

        List<ScanSummaryResponse> history = service.getHistory(owner, 1L);

        assertThat(history).hasSize(2);
    }

    @Test
    void getTrendReportsInsufficientDataWithFewerThanTwoCompletedScans() {
        Scan onlyCompleted = completedScanAt(Instant.now(), 90);
        when(scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()))
                .thenReturn(List.of(onlyCompleted));

        TrendResponse trend = service.getTrend(owner, 1L);

        assertThat(trend.direction()).isEqualTo(TrendDirection.INSUFFICIENT_DATA);
        assertThat(trend.scoreDelta()).isNull();
        assertThat(trend.completedScans()).hasSize(1);
    }

    @Test
    void getTrendClassifiesAScoreIncreaseAsImproved() {
        Scan older = completedScanAt(Instant.now().minus(2, ChronoUnit.DAYS), 50);
        Scan newer = completedScanAt(Instant.now(), 75);
        when(scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()))
                .thenReturn(List.of(newer, older));

        TrendResponse trend = service.getTrend(owner, 1L);

        assertThat(trend.direction()).isEqualTo(TrendDirection.IMPROVED);
        assertThat(trend.scoreDelta()).isEqualTo(25);
        assertThat(trend.completedScans()).extracting(ScanSummaryResponse::overallScore)
                .containsExactly(50, 75);
    }

    @Test
    void getTrendClassifiesAScoreDecreaseAsWorsened() {
        Scan older = completedScanAt(Instant.now().minus(2, ChronoUnit.DAYS), 90);
        Scan newer = completedScanAt(Instant.now(), 60);
        when(scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()))
                .thenReturn(List.of(newer, older));

        TrendResponse trend = service.getTrend(owner, 1L);

        assertThat(trend.direction()).isEqualTo(TrendDirection.WORSENED);
        assertThat(trend.scoreDelta()).isEqualTo(-30);
    }

    @Test
    void getTrendClassifiesAnIdenticalScoreAsUnchanged() {
        Scan older = completedScanAt(Instant.now().minus(2, ChronoUnit.DAYS), 70);
        Scan newer = completedScanAt(Instant.now(), 70);
        when(scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()))
                .thenReturn(List.of(newer, older));

        TrendResponse trend = service.getTrend(owner, 1L);

        assertThat(trend.direction()).isEqualTo(TrendDirection.UNCHANGED);
        assertThat(trend.scoreDelta()).isZero();
    }

    @Test
    void getTrendIgnoresInProgressAndFailedScans() {
        Scan inProgress = scanAt(Instant.now(), null, null);
        Scan failed = scanAt(Instant.now().minus(1, ChronoUnit.DAYS), null, null);
        failed.setStatus(ScanStatus.FAILED);
        Scan onlyCompleted = completedScanAt(Instant.now().minus(2, ChronoUnit.DAYS), 65);
        when(scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()))
                .thenReturn(List.of(inProgress, failed, onlyCompleted));

        TrendResponse trend = service.getTrend(owner, 1L);

        assertThat(trend.direction()).isEqualTo(TrendDirection.INSUFFICIENT_DATA);
        assertThat(trend.completedScans()).hasSize(1);
    }

    private Scan completedScanAt(Instant startedAt, int score) {
        Scan scan = scanAt(startedAt, score, RiskRating.LOW);
        scan.setStatus(ScanStatus.COMPLETED);
        return scan;
    }

    private Scan scanAt(Instant startedAt, Integer score, RiskRating riskRating) {
        Scan scan = new Scan(website);
        // startedAt is normally set by @CreationTimestamp on save; set
        // directly here since these are never persisted in this test.
        try {
            var field = Scan.class.getDeclaredField("startedAt");
            field.setAccessible(true);
            field.set(scan, startedAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        scan.setOverallScore(score);
        scan.setRiskRating(riskRating);
        return scan;
    }
}
