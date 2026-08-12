package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.Finding;
import com.sentinel.secscan.domain.FindingRepository;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanRepository;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.scan.dto.ScanResponse;
import com.sentinel.secscan.website.WebsiteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Triggering a scan only creates the Scan row and hands off to ScanRunner
 * (Day 12's async execution, see its own class comment for why); it does
 * not wait for the scan to finish. Same ownership scoping as WebsiteService
 * throughout: a scan (via its website) can never be reached by anyone but
 * its owner.
 */
@Service
public class ScanService {

    private final ScanRepository scanRepository;
    private final FindingRepository findingRepository;
    private final WebsiteService websiteService;
    private final ScanRunner scanRunner;

    public ScanService(
            ScanRepository scanRepository,
            FindingRepository findingRepository,
            WebsiteService websiteService,
            ScanRunner scanRunner) {
        this.scanRepository = scanRepository;
        this.findingRepository = findingRepository;
        this.websiteService = websiteService;
        this.scanRunner = scanRunner;
    }

    public ScanResponse startScan(User currentUser, Long websiteId) {
        Website website = websiteService.getEntityForOwner(currentUser, websiteId);
        Scan scan = scanRepository.save(new Scan(website));
        scanRunner.run(scan.getId(), website);
        return ScanResponse.from(scan, List.of());
    }

    public ScanResponse getForOwner(User currentUser, Long scanId) {
        Scan scan = scanRepository.findByIdAndWebsiteOwnerId(scanId, currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found"));
        List<Finding> findings = findingRepository.findByScanId(scan.getId());
        return ScanResponse.from(scan, findings);
    }
}
