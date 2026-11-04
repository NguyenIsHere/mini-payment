package com.example.mini_payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 5 luồng chạy thường trực
        executor.setMaxPoolSize(10);        // Tối đa 10 luồng khi quá tải
        executor.setQueueCapacity(100);     // Hàng chờ chứa 100 task
        executor.setThreadNamePrefix("PaymentAsyncThread-");
        executor.initialize();
        return executor;
    }
}