package com.sentinel.secscan.website;

import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.website.dto.WebsiteRequest;
import com.sentinel.secscan.website.dto.WebsiteResponse;
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

@RestController
@RequestMapping("/api/websites")
public class WebsiteController {

    private final WebsiteService websiteService;

    public WebsiteController(WebsiteService websiteService) {
        this.websiteService = websiteService;
    }

    @PostMapping
    public ResponseEntity<WebsiteResponse> register(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody WebsiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(websiteService.register(currentUser, request));
    }

    @GetMapping
    public List<WebsiteResponse> list(@AuthenticationPrincipal User currentUser) {
        return websiteService.listForOwner(currentUser);
    }

    @GetMapping("/{id}")
    public WebsiteResponse get(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return websiteService.getForOwner(currentUser, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        websiteService.deleteForOwner(currentUser, id);
        return ResponseEntity.noContent().build();
    }
}
