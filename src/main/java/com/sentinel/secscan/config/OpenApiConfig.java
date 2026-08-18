package com.sentinel.secscan.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Day 17: springdoc-openapi generates the OpenAPI spec and Swagger UI
 * straight from the existing controllers and DTOs at runtime, no
 * hand-maintained spec file to drift out of sync with the real API.
 * This class only supplies the metadata springdoc can't infer on its
 * own: the API's title/description, and the JWT bearer scheme so
 * Swagger UI's "Authorize" button can attach a token to protected
 * requests and actually exercise them from the browser.
 *
 * The "bearerAuth" requirement below applies globally to every
 * endpoint by default; AuthController's two public endpoints
 * (register, login) override it back off with their own empty
 * @SecurityRequirements(), so the generated spec accurately reflects
 * what SecurityConfig actually enforces (FR-1.3 in docs/SRS.md:
 * everything except those two endpoints requires a valid JWT), rather
 * than showing a lock icon on endpoints that don't need one.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Sentinel API",
                version = "0.1.0",
                description = "Passive web security assessment platform. Register a website, "
                        + "trigger a scan, and retrieve findings, a score, and a PDF report. "
                        + "Every check is passive only, no active scanning, exploitation, or "
                        + "brute force, ever (see docs/SRS.md NFR-1 in the repository)."
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
