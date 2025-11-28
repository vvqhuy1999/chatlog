package com.example.chatlog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration cho async processing và thread pools
 * Tối ưu cho parallel AI calls (OpenAI + OpenRouter)
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * ExecutorService cho AI parallel processing
     * - Sử dụng Virtual Threads (Java 21+) nếu có
     * - Fallback về cached thread pool nếu không
     */
    @Bean(name = "aiExecutor")
    public ExecutorService aiExecutor() {
        try {
            // Java 21+ Virtual Threads - tối ưu cho I/O-bound tasks như API calls
            return Executors.newVirtualThreadPerTaskExecutor();
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // Fallback cho Java < 21
            System.out.println("[AsyncConfig] Virtual threads not available, using cached thread pool");
            return Executors.newCachedThreadPool();
        }
    }

    /**
     * TaskExecutor cho Spring @Async methods
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

