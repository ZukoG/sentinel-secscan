package com.sentinel.secscan.auth;

import com.sentinel.secscan.auth.dto.AuthResponse;
import com.sentinel.secscan.auth.dto.LoginRequest;
import com.sentinel.secscan.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Day 17: @Tag/@Operation/@SecurityRequirements added for the generated
// OpenAPI spec. No behavior change, annotation only.
@Tag(name = "Auth", description = "Register and log in. The only endpoints that don't require a JWT.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new account", description = "Passwords are BCrypt hashed. Does not log the user in, see /login for that.")
    @SecurityRequirements // overrides the global bearerAuth requirement: this endpoint is public
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Log in and receive a JWT", description = "Returns the same generic error for a wrong password and a nonexistent email, to avoid user enumeration.")
    @SecurityRequirements // overrides the global bearerAuth requirement: this endpoint is public
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
