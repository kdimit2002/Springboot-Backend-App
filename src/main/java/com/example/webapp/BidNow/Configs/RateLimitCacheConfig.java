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
     * Keep a user in cache for 5 minutes
     * from the previous request
     *
     */
    @Bean("rateLimitStateCache")
    public Cache<String, RateLimitState> rateLimitStateCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5)) //
                .maximumSize(100_000)
                .recordStats()
                .build();
    }

    /**
     *  Keep user that is banned in cache for
     *  1 hour in order to cut his connection with
     *  the app server
     *
     */
    @Bean("rateLimitBanCache")
    public Cache<String, Long> rateLimitBanCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1).plusMinutes(10)) // Keep users banned for 1 hour.
                .maximumSize(100_000)
                .build();
    }
}
