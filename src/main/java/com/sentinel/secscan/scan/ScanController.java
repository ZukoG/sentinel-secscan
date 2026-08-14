package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.report.ReportService;
import com.sentinel.secscan.scan.dto.ScanResponse;
import com.sentinel.secscan.scan.dto.ScanSummaryResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Scan creation is nested under its website (POST /api/websites/{id}/scans,
 * matching the wiki's sequence diagram), but reading a scan is top-level
 * by its own id (GET /api/scans/{id}), a common, defensible REST split:
 * create under the parent, address the created resource by its own
 * identity afterward. The report endpoint (Day 13) and history/trend
 * endpoints (Day 14) live here too rather than in their own controllers,
 * all scan-resource-scoped, matching the plan's own file lists (no
 * separate ReportController or ScanHistoryController).
 */
@RestController
public class ScanController {

    private final ScanService scanService;
    private final ReportService reportService;
    private final ScanHistoryService scanHistoryService;

    public ScanController(ScanService scanService, ReportService reportService, ScanHistoryService scanHistoryService) {
        this.scanService = scanService;
        this.reportService = reportService;
        this.scanHistoryService = scanHistoryService;
    }

    @PostMapping("/api/websites/{websiteId}/scans")
    public ResponseEntity<ScanResponse> startScan(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long websiteId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scanService.startScan(currentUser, websiteId));
    }

    @GetMapping("/api/websites/{websiteId}/scans")
    public List<ScanSummaryResponse> getHistory(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long websiteId) {
        return scanHistoryService.getHistory(currentUser, websiteId);
    }

    @GetMapping("/api/scans/{id}")
    public ScanResponse getScan(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return scanService.getForOwner(currentUser, id);
    }

    @GetMapping("/api/scans/{id}/report")
    public ResponseEntity<byte[]> getReport(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        byte[] pdf = reportService.generateReport(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scan-" + id + "-report.pdf\"")
                .body(pdf);
    }
}
