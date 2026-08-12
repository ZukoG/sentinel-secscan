package com.sentinel.secscan.website;

import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.Website;
import com.sentinel.secscan.domain.WebsiteRepository;
import com.sentinel.secscan.website.dto.WebsiteRequest;
import com.sentinel.secscan.website.dto.WebsiteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Websites are always looked up scoped to the current user (findByOwnerId /
 * findByIdAndOwnerId), never fetched by id alone and checked afterward. A
 * user structurally cannot reach another user's website through this
 * service (FR-2.3 in docs/SRS.md), there's no separate authorization check
 * to forget. A website that exists but belongs to someone else returns
 * 404, not 403, same "don't confirm it exists" reasoning as the generic
 * login error from Day 4.
 */
@Service
public class WebsiteService {

    private final WebsiteRepository websiteRepository;

    public WebsiteService(WebsiteRepository websiteRepository) {
        this.websiteRepository = websiteRepository;
    }

    public WebsiteResponse register(User owner, WebsiteRequest request) {
        validateUrl(request.url());
        Website website = new Website(owner, request.url());
        return WebsiteResponse.from(websiteRepository.save(website));
    }

    public List<WebsiteResponse> listForOwner(User owner) {
        return websiteRepository.findByOwnerId(owner.getId()).stream()
                .map(WebsiteResponse::from)
                .toList();
    }

    public WebsiteResponse getForOwner(User owner, Long websiteId) {
        return WebsiteResponse.from(findOwned(owner, websiteId));
    }

    // Added Day 12: ScanService needs the real Website entity (to run
    // checks against and attach a new Scan to), not the response DTO.
    // Reuses the same ownership-scoped lookup as every other method here.
    public Website getEntityForOwner(User owner, Long websiteId) {
        return findOwned(owner, websiteId);
    }

    public void deleteForOwner(User owner, Long websiteId) {
        websiteRepository.delete(findOwned(owner, websiteId));
    }

    private Website findOwned(User owner, Long websiteId) {
        return websiteRepository.findByIdAndOwnerId(websiteId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website not found"));
    }

    /**
     * Bean validation handles "not blank", this handles "well-formed and
     * http/https only" (FR-2.4). Kept as explicit code rather than a
     * Hibernate Validator @URL(regexp = ...) combo, easier to read and
     * gives a precise error message for each failure case.
     */
    private void validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must use http or https");
        }
    }
}
