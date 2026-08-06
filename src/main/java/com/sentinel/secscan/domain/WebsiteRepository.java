package com.sentinel.secscan.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebsiteRepository extends JpaRepository<Website, Long> {

    List<Website> findByOwnerId(Long ownerId);

    Optional<Website> findByIdAndOwnerId(Long id, Long ownerId);
}
