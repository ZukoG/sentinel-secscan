package com.sentinel.secscan.website.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebsiteRequest(
        @NotBlank @Size(max = 2048) String url
) {
}
