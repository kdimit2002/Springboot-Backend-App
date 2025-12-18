package com.example.webapp.BidNow.Configs;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.webapp.BidNow.Security.RateLimiterService.RateLimitState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
//Todo: we also have to use external rate limiter(in load balancer)

/**
 * Configuration class for in-memory request rate limiting.
 * This cache stores per-user rate limiting state,
 * including request counters.
 */
@Configuration
public class RateLimitCacheConfig {

    /**
     * Keep a user in cache for 15 minutes
     * from the previous request
     *
     */
    @Bean("rateLimitStateCache")
    public Cache<String, RateLimitState> rateLimitStateCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(15)) // κρατά state για active keys
                .maximumSize(100_000)
                .recordStats()
                .build();
    }

    /**
     *  Keep user that is banned in cache for
     *  2 hours in order to cut his connection with
     *  the app server
     *
     */
    @Bean("rateLimitBanCache")
    public Cache<String, Long> rateLimitBanCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(2)) // safety cleanup
                .maximumSize(100_000)
                .build();
    }

    /**
     * After we send an email alert of
     * rate limit violation we write to this
     * cache in order to avoid email spamming
     * @return
     */
    @Bean("rateLimitAlertCooldownCache")
    public Cache<String, Boolean> rateLimitAlertCooldownCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10)) // 1 alert / 10'
                .maximumSize(100_000)
                .build();
    }
}
