package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanRepository;
import com.sentinel.secscan.domain.ScanStatus;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scan.dto.ScanSummaryResponse;
import com.sentinel.secscan.scan.dto.TrendDirection;
import com.sentinel.secscan.scan.dto.TrendResponse;
import com.sentinel.secscan.website.WebsiteService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Ownership is checked once, up front, by resolving the website through
 * WebsiteService (same 404-not-403 pattern as everywhere else in this
 * codebase); every scan lookup here then trusts that already-authorized
 * website id, same approach ScanService.startScan already uses.
 *
 * No "noise threshold" for what counts as an unchanged score: exact
 * equality is unambiguous, anything else would be an arbitrary judgment
 * call with no principled basis yet.
 */
@Service
public class ScanHistoryService {

    private final ScanRepository scanRepository;
    private final WebsiteService websiteService;

    public ScanHistoryService(ScanRepository scanRepository, WebsiteService websiteService) {
        this.scanRepository = scanRepository;
        this.websiteService = websiteService;
    }

    public List<ScanSummaryResponse> getHistory(User currentUser, Long websiteId) {
        Website website = websiteService.getEntityForOwner(currentUser, websiteId);
        return scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()).stream()
                .map(ScanSummaryResponse::from)
                .toList();
    }

    public TrendResponse getTrend(User currentUser, Long websiteId) {
        Website website = websiteService.getEntityForOwner(currentUser, websiteId);

        List<Scan> completed = scanRepository.findByWebsiteIdOrderByStartedAtDesc(website.getId()).stream()
                .filter(scan -> scan.getStatus() == ScanStatus.COMPLETED)
                .sorted(Comparator.comparing(Scan::getStartedAt))
                .toList();

        List<ScanSummaryResponse> summaries = completed.stream().map(ScanSummaryResponse::from).toList();

        if (completed.size() < 2) {
            return new TrendResponse(summaries, null, TrendDirection.INSUFFICIENT_DATA);
        }

        Scan previous = completed.get(completed.size() - 2);
        Scan latest = completed.get(completed.size() - 1);
        int delta = latest.getOverallScore() - previous.getOverallScore();
        TrendDirection direction = delta > 0 ? TrendDirection.IMPROVED
                : delta < 0 ? TrendDirection.WORSENED
                : TrendDirection.UNCHANGED;

        return new TrendResponse(summaries, delta, direction);
    }
}
