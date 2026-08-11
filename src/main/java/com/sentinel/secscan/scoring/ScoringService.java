package com.sentinel.secscan.scoring;

import com.sentinel.secscan.domain.RiskRating;
import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.scanner.CheckResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Operates on CheckResult, not the Finding entity, same reasoning as
 * ScanCheck.run() (Day 7): scoring happens right after the scanner engine
 * runs, before any Scan/Finding rows exist to read from (that's Day 12's
 * job). Day 12's ScanService calls this directly with ScannerEngine's
 * output.
 *
 * Weighting is severity-based only, not also weighted per-check. Each
 * check already assigns its own severity based on domain knowledge of how
 * bad that specific outcome is, stacking a second, separate per-check
 * multiplier on top would need its own justification this project has no
 * principled basis for yet. Resolves the scoring open question flagged in
 * docs/SRS.md section 7.
 *
 * Known limitation, not fixed here: a check that couldn't complete (e.g.
 * "could not verify HTTPS connectivity", severity MEDIUM) currently
 * scores the same as a genuine MEDIUM misconfiguration. Distinguishing
 * "inconclusive" from "confirmed issue" would mean adding a new field to
 * CheckResult and touching all six existing checks, more than this day's
 * scope covers.
 */
@Service
public class ScoringService {

    // Points deducted per finding at each severity. First-pass numbers,
    // not derived from an external standard, calibrated so a handful of
    // MEDIUM issues meaningfully move the score without a single CRITICAL
    // finding alone being unrecoverable.
    private static final Map<Severity, Integer> DEDUCTIONS = Map.of(
            Severity.INFO, 0,
            Severity.LOW, 5,
            Severity.MEDIUM, 15,
            Severity.HIGH, 25,
            Severity.CRITICAL, 40
    );

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    public int calculateScore(List<CheckResult> results) {
        int score = MAX_SCORE;
        for (CheckResult result : results) {
            score -= DEDUCTIONS.getOrDefault(result.severity(), 0);
        }
        return Math.max(score, MIN_SCORE);
    }

    public RiskRating determineRiskRating(int score) {
        if (score >= 90) {
            return RiskRating.LOW;
        }
        if (score >= 70) {
            return RiskRating.MEDIUM;
        }
        if (score >= 40) {
            return RiskRating.HIGH;
        }
        return RiskRating.CRITICAL;
    }
}
