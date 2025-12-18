package com.example.webapp.BidNow.Configs;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache manager for auction details
 * to be retrieved as fast as possible
 */
@Configuration
public class CacheConfig {

    /**
     * An instance in cache(id,auction) must be cleaned up
     * 2 hours after it's last write/update
     * @return
     */
    @Bean
    public Caffeine caffeineConfig() {
        return Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.HOURS)// TODO: Use @CacheEvict or @CachePut in the update flow to keep cache consistent.
                .maximumSize(500).recordStats();//todo: lower this time after many users!
    }

    /**
     * Initialize cache manager,
     * @param caffeine
     * @return
     */
    @Bean
    public CacheManager cacheManager(Caffeine caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
}
