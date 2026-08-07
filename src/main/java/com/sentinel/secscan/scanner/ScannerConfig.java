package com.sentinel.secscan.scanner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * One shared HttpClient for every check that needs to make a request, the
 * JDK's own docs recommend creating one and reusing it rather than a new
 * instance per call, it pools connections. Redirects are never followed
 * automatically: checks that care about redirect behavior (HttpsCheck,
 * RedirectAnalysisCheck later) need to see the 3xx response itself, not
 * have it silently resolved away.
 */
@Configuration
public class ScannerConfig {

    @Bean
    public HttpClient scannerHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
