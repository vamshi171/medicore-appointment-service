package com.medicore.appointment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for async notification dispatch.
 * Demonstrates multithreading: bounded pool, queue capacity, thread naming,
 * and graceful shutdown — not raw new Thread() everywhere.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "medicoreTaskExecutor")
    public Executor medicoreTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("medicore-notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
