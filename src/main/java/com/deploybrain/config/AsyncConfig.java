package com.deploybrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * Dedicated pool for agent orchestration runs, sized to 1 so agent
     * runs execute strictly one at a time. This matches Ollama's
     * single-concurrent-model-slot behavior in this Docker setup -
     * without this, multiple simultaneous builds all queue against
     * Ollama at once and each queued request's timeout clock keeps
     * ticking while it waits, causing every single one to fail.
     */
    @Bean(name = "agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-run-");
        executor.initialize();
        return executor;
    }
}