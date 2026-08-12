package com.sentinel.secscan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * A scan runs six checks that each make real HTTP requests, easily
 * taking 10+ seconds combined. FR-5.1 in docs/SRS.md requires the
 * triggering request not to block indefinitely, so scan execution
 * (ScanRunner, Day 12) runs on this dedicated pool instead of the
 * calling thread. Sized modestly, this is a small project, not a
 * high-concurrency service.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "scanTaskExecutor")
    public Executor scanTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("scan-");
        executor.initialize();
        return executor;
    }
}
