package com.example.webapp.BidNow.Configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * Async configuration file
 * Here we created 3 thread pools for performance optimization
 * These are non-critical background tasks, application works correctly even if these fail
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * User Logging for data analysis and admin
     * critical operations tracking purposes
     */
    @Bean(name = "userActivityExecutor")
    public Executor userActivityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // Threads waiting in the pool
        executor.setMaxPoolSize(6);       // Maximum allowed threads in pool
        executor.setQueueCapacity(300);    // Maximum tasks in queue
        executor.setThreadNamePrefix("user-activity-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }


    /**
     * User notifications
     *
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notifications-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }


    /**
     * Email for errors or user notification
     *
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);

        executor.setQueueCapacity(1000);

        executor.setThreadNamePrefix("email-async-");

        // Reject task if queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        executor.initialize();
        return executor;
    }
}
