package com.sentinel.secscan.report;

import com.sentinel.secscan.domain.Finding;
import com.sentinel.secscan.domain.FindingRepository;
import com.sentinel.secscan.domain.Scan;
import com.sentinel.secscan.domain.ScanRepository;
import com.sentinel.secscan.domain.ScanStatus;
import com.sentinel.secscan.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Generates the PDF fresh from already-persisted Scan/Finding data on
 * every request, rather than persisting a Report entity/file to disk as
 * the original ERD sketch showed. Everything a report needs already
 * lives in the database (Day 12); a stored PDF would need a durable
 * volume mount that doesn't exist anywhere in this project's Docker
 * setup (container filesystems are ephemeral), so regenerating on demand
 * is simpler and avoids a real infrastructure gap, not just a shortcut.
 */
@Service
public class ReportService {

    private final ScanRepository scanRepository;
    private final FindingRepository findingRepository;
    private final PdfReportGenerator pdfReportGenerator;

    public ReportService(
            ScanRepository scanRepository,
            FindingRepository findingRepository,
            PdfReportGenerator pdfReportGenerator) {
        this.scanRepository = scanRepository;
        this.findingRepository = findingRepository;
        this.pdfReportGenerator = pdfReportGenerator;
    }

    public byte[] generateReport(User currentUser, Long scanId) {
        Scan scan = scanRepository.findByIdAndWebsiteOwnerId(scanId, currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found"));

        if (scan.getStatus() == ScanStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Scan is still in progress");
        }
        if (scan.getStatus() == ScanStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Scan failed, no report is available");
        }

        List<Finding> findings = findingRepository.findByScanId(scan.getId());
        return pdfReportGenerator.generate(scan, findings);
    }
}
