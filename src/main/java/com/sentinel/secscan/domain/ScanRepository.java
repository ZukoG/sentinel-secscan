package com.sentinel.secscan.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    Optional<Scan> findByIdAndWebsiteOwnerId(Long id, Long ownerId);
}
