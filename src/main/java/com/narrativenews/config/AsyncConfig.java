package com.narrativenews.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {
    /** One render at a time keeps memory low on free hosting. Extra uploads wait in the queue. */
    @Bean("renderExecutor")
    public Executor renderExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(10);
        ex.setThreadNamePrefix("render-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return ex;
    }
}
