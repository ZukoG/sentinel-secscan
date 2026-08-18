package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.report.ReportService;
import com.sentinel.secscan.scan.dto.ScanResponse;
import com.sentinel.secscan.scan.dto.ScanSummaryResponse;
import com.sentinel.secscan.scan.dto.TrendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *
 * Day 17: @Tag/@Operation/@ApiResponse added for the generated OpenAPI
 * spec. No behavior change, annotation only.
 */
@Tag(name = "Scans", description = "Trigger scans, poll their status and findings, view history/trend, and download a PDF report.")
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

    @Operation(summary = "Trigger a scan", description = "Returns immediately with status IN_PROGRESS; scanning runs asynchronously (FR-5.1). Poll GET /api/scans/{id} for completion.")
    @PostMapping("/api/websites/{websiteId}/scans")
    public ResponseEntity<ScanResponse> startScan(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long websiteId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scanService.startScan(currentUser, websiteId));
    }

    @Operation(summary = "List a website's scan history", description = "Most recent first, every status included.")
    @GetMapping("/api/websites/{websiteId}/scans")
    public List<ScanSummaryResponse> getHistory(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long websiteId) {
        return scanHistoryService.getHistory(currentUser, websiteId);
    }

    @Operation(summary = "Get the score trend for a website", description = "Chronological, completed scans only, plus a delta/direction comparing the two most recent.")
    @GetMapping("/api/websites/{websiteId}/scans/trend")
    public TrendResponse getTrend(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long websiteId) {
        return scanHistoryService.getTrend(currentUser, websiteId);
    }

    @Operation(summary = "Get one scan, including findings", description = "404 if it doesn't exist or belongs to another user.")
    @GetMapping("/api/scans/{id}")
    public ScanResponse getScan(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return scanService.getForOwner(currentUser, id);
    }

    @Operation(summary = "Download a scan's PDF report", description = "409 if the scan isn't COMPLETED yet.")
    @ApiResponse(responseCode = "200", description = "PDF report", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/api/scans/{id}/report")
    public ResponseEntity<byte[]> getReport(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        byte[] pdf = reportService.generateReport(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scan-" + id + "-report.pdf\"")
                .body(pdf);
    }
}
