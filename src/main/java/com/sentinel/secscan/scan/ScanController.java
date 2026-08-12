package com.sentinel.secscan.scan;

import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.scan.dto.ScanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scan creation is nested under its website (POST /api/websites/{id}/scans,
 * matching the wiki's sequence diagram), but reading a scan is top-level
 * by its own id (GET /api/scans/{id}), a common, defensible REST split:
 * create under the parent, address the created resource by its own
 * identity afterward.
 */
@RestController
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/api/websites/{websiteId}/scans")
    public ResponseEntity<ScanResponse> startScan(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long websiteId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scanService.startScan(currentUser, websiteId));
    }

    @GetMapping("/api/scans/{id}")
    public ScanResponse getScan(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return scanService.getForOwner(currentUser, id);
    }
}
