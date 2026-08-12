package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.Finding;
import com.sentinel.secscan.domain.FindingRepository;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanRepository;
import com.sentinel.secscan.domain.ScanStatus;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scanner.CheckResult;
import com.sentinel.secscan.scanner.ScannerEngine;
import com.sentinel.secscan.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * A separate bean from ScanService on purpose: @Async only works through
 * Spring's proxy, calling an @Async method from another method in the
 * same class (self-invocation) silently runs it synchronously instead,
 * a well known Spring pitfall. Splitting it out avoids that structurally.
 *
 * Takes the already-loaded Website directly rather than reading it off
 * the Scan's website association (FetchType.LAZY), which would risk a
 * LazyInitializationException once this runs on a different thread after
 * the original transaction that loaded the Scan has closed.
 */
@Component
public class ScanRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanRunner.class);

    private final ScannerEngine scannerEngine;
    private final ScoringService scoringService;
    private final ScanRepository scanRepository;
    private final FindingRepository findingRepository;

    public ScanRunner(
            ScannerEngine scannerEngine,
            ScoringService scoringService,
            ScanRepository scanRepository,
            FindingRepository findingRepository) {
        this.scannerEngine = scannerEngine;
        this.scoringService = scoringService;
        this.scanRepository = scanRepository;
        this.findingRepository = findingRepository;
    }

    @Async("scanTaskExecutor")
    public void run(Long scanId, Website website) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new IllegalStateException("Scan " + scanId + " was deleted before it could run"));

        try {
            List<CheckResult> results = scannerEngine.runAll(website);

            List<Finding> findings = results.stream()
                    .map(r -> new Finding(scan, r.checkName(), r.severity(), r.description(), r.recommendation()))
                    .toList();
            findingRepository.saveAll(findings);

            int score = scoringService.calculateScore(results);
            scan.setOverallScore(score);
            scan.setRiskRating(scoringService.determineRiskRating(score));
            scan.setStatus(ScanStatus.COMPLETED);
        } catch (Exception e) {
            // ScannerEngine already converts individual check failures into
            // CheckResults (FR-3.4), so reaching here means something
            // outside that contract broke. Mark the scan FAILED rather
            // than leaving it stuck IN_PROGRESS forever.
            log.error("Scan {} failed unexpectedly", scanId, e);
            scan.setStatus(ScanStatus.FAILED);
        } finally {
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        }
    }
}
