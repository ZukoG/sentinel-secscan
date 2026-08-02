package com.sentinel.secscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One result from one ScanCheck (Day 7+) against one website. description
 * and recommendation are TEXT columns rather than VARCHAR, some checks
 * (e.g. SSL certificate chain issues) produce longer explanations than a
 * short varchar comfortably holds.
 */
@Entity
@Table(name = "findings")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Column(name = "check_name", nullable = false)
    private String checkName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "recommendation", nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    protected Finding() {
        // required by JPA, not for direct use
    }

    public Finding(Scan scan, String checkName, Severity severity, String description, String recommendation) {
        this.scan = scan;
        this.checkName = checkName;
        this.severity = severity;
        this.description = description;
        this.recommendation = recommendation;
    }

    public Long getId() {
        return id;
    }

    public Scan getScan() {
        return scan;
    }

    public String getCheckName() {
        return checkName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
