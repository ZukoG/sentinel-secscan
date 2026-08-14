package com.sentinel.secscan.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    Optional<Scan> findByIdAndWebsiteOwnerId(Long id, Long ownerId);

    // Added Day 14: history/trend both need every scan for a website.
    // Ownership is checked once by the caller (via WebsiteService,
    // before this is ever called with a websiteId), same pattern as
    // ScanService.startScan already uses, so this doesn't need its own
    // owner-scoped variant.
    List<Scan> findByWebsiteIdOrderByStartedAtDesc(Long websiteId);
}
