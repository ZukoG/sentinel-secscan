package com.sentinel.secscan.website.dto;

import com.sentinel.secscan.domain.Website;

import java.time.Instant;

public record WebsiteResponse(Long id, String url, Instant addedAt) {

    public static WebsiteResponse from(Website website) {
        return new WebsiteResponse(website.getId(), website.getUrl(), website.getAddedAt());
    }
}
