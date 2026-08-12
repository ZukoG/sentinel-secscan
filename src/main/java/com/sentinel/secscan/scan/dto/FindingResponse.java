package com.sentinel.secscan.scan.dto;

import com.sentinel.secscan.domain.Finding;
import com.sentinel.secscan.domain.Severity;

public record FindingResponse(String checkName, Severity severity, String description, String recommendation) {

    public static FindingResponse from(Finding finding) {
        return new FindingResponse(
                finding.getCheckName(),
                finding.getSeverity(),
                finding.getDescription(),
                finding.getRecommendation());
    }
}
