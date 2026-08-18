package com.sentinel.secscan.website;

import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.website.dto.WebsiteRequest;
import com.sentinel.secscan.website.dto.WebsiteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Day 17: @Tag/@Operation added for the generated OpenAPI spec. No
// behavior change, annotation only.
@Tag(name = "Websites", description = "Register and manage the websites a user wants assessed. Every lookup is scoped to the authenticated user, another user's website returns 404, not 403.")
@RestController
@RequestMapping("/api/websites")
public class WebsiteController {

    private final WebsiteService websiteService;

    public WebsiteController(WebsiteService websiteService) {
        this.websiteService = websiteService;
    }

    @Operation(summary = "Register a website", description = "URL must be well-formed and use http or https.")
    @PostMapping
    public ResponseEntity<WebsiteResponse> register(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody WebsiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(websiteService.register(currentUser, request));
    }

    @Operation(summary = "List the current user's websites")
    @GetMapping
    public List<WebsiteResponse> list(@AuthenticationPrincipal User currentUser) {
        return websiteService.listForOwner(currentUser);
    }

    @Operation(summary = "Get one website by id", description = "404 if it doesn't exist or belongs to another user.")
    @GetMapping("/{id}")
    public WebsiteResponse get(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return websiteService.getForOwner(currentUser, id);
    }

    @Operation(summary = "Delete a website", description = "404 if it doesn't exist or belongs to another user.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        websiteService.deleteForOwner(currentUser, id);
        return ResponseEntity.noContent().build();
    }
}
