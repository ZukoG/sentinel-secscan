package com.sentinel.secscan.scoring;

import com.sentinel.secscan.domain.RiskRating;
import com.sentinel.secscan.domain.Severity;
import com.sentinel.secscan.scanner.CheckResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure logic, no network or persistence involved, so this covers every
 * severity deduction and every risk-rating band boundary directly.
 */
class ScoringServiceTest {

    private final ScoringService scoringService = new ScoringService();

    @Test
    void emptyResultsScorePerfect() {
        assertThat(scoringService.calculateScore(List.of())).isEqualTo(100);
    }

    @Test
    void allInfoResultsScorePerfect() {
        List<CheckResult> results = List.of(
                result(Severity.INFO), result(Severity.INFO), result(Severity.INFO));

        assertThat(scoringService.calculateScore(results)).isEqualTo(100);
    }

    @Test
    void deductsPointsPerSeverity() {
        List<CheckResult> results = List.of(
                result(Severity.LOW), result(Severity.MEDIUM),
                result(Severity.HIGH), result(Severity.CRITICAL));

        // 100 - (5 + 15 + 25 + 40)
        assertThat(scoringService.calculateScore(results)).isEqualTo(15);
    }

    @Test
    void scoreNeverGoesBelowZero() {
        List<CheckResult> results = List.of(
                result(Severity.CRITICAL), result(Severity.CRITICAL),
                result(Severity.CRITICAL), result(Severity.CRITICAL));

        assertThat(scoringService.calculateScore(results)).isEqualTo(0);
    }

    @Test
    void ratesLowAtNinetyAndAbove() {
        assertThat(scoringService.determineRiskRating(100)).isEqualTo(RiskRating.LOW);
        assertThat(scoringService.determineRiskRating(90)).isEqualTo(RiskRating.LOW);
    }

    @Test
    void ratesMediumBelowNinety() {
        assertThat(scoringService.determineRiskRating(89)).isEqualTo(RiskRating.MEDIUM);
        assertThat(scoringService.determineRiskRating(70)).isEqualTo(RiskRating.MEDIUM);
    }

    @Test
    void ratesHighBelowSeventy() {
        assertThat(scoringService.determineRiskRating(69)).isEqualTo(RiskRating.HIGH);
        assertThat(scoringService.determineRiskRating(40)).isEqualTo(RiskRating.HIGH);
    }

    @Test
    void ratesCriticalBelowForty() {
        assertThat(scoringService.determineRiskRating(39)).isEqualTo(RiskRating.CRITICAL);
        assertThat(scoringService.determineRiskRating(0)).isEqualTo(RiskRating.CRITICAL);
    }

    private CheckResult result(Severity severity) {
        return new CheckResult("test-check", severity, "description", "recommendation");
    }
}
