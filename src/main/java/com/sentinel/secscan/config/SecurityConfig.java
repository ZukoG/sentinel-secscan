package com.sentinel.secscan.config;

import com.sentinel.secscan.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, JWT-only setup: no form login, no HTTP Basic, no server-side
 * sessions. /api/auth/** is open (that's how you get a token in the first
 * place), everything else requires one, per FR-1.3 in docs/SRS.md.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Spring Boot forwards failed requests to /error
                        // internally, and that forward re-enters this same
                        // filter chain. Without this, an exception thrown by
                        // a permitted endpoint (e.g. a 409 on duplicate
                        // registration) gets masked behind a 401 from the
                        // entry point below, since the forwarded request has
                        // no authenticated principal either.
                        .requestMatchers("/error").permitAll()
                        // Day 17: the generated OpenAPI spec and Swagger UI
                        // only describe the API's shape (paths, DTOs,
                        // status codes), they don't expose any user data,
                        // so there's no reason to gate them behind a JWT.
                        // Every actual endpoint they document still enforces
                        // its own auth per the rules above.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                // Without this, an unauthenticated request to a protected
                // endpoint falls back to Spring Security's default 403
                // handler. 401 is the semantically correct response for
                // "no valid credentials were provided at all".
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
