//package com.example.webapp.BidNow.Configs;
//
//import com.github.benmanes.caffeine.cache.Caffeine;
//import org.springframework.cache.CacheManager;
//import org.springframework.cache.caffeine.CaffeineCache;
//import org.springframework.cache.caffeine.CaffeineCacheManager;
//import org.springframework.cache.support.SimpleCacheManager;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
///**
// * Cache manager for auction details
// * to be retrieved as fast as possible
// */
//@Configuration
//public class CacheConfig {
//
////    /**
////     * An instance in cache(id,auction) must be cleaned up
////     * 2 hours after it's last write/update
////     * @return
////     */
////    @Bean
////    public Caffeine caffeineConfig() {
////        return Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.HOURS)// TODO: Use @CacheEvict or @CachePut in the update flow to keep cache consistent.
////                .maximumSize(500).recordStats();//todo: lower this time after many users!
////    }
////
////    /**
////     * Initialize cache manager,
////     * @param caffeine
////     * @return
////     */
////    @Bean
////    public CacheManager cacheManager(Caffeine caffeine) {
////        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
////        caffeineCacheManager.setCaffeine(caffeine);
////        return caffeineCacheManager;
////    }
//
//    public static final String AUCTIONS_DEFAULT_CACHE = "auctionsDefault";
//
//    @Bean
//    public CacheManager cacheManager() {
//        SimpleCacheManager manager = new SimpleCacheManager();
//
//        CaffeineCache auctionsDefault = new CaffeineCache(
//                AUCTIONS_DEFAULT_CACHE,
//                Caffeine.newBuilder()
//                        .expireAfterWrite(10, TimeUnit.MINUTES) // ή ό,τι θες
//                        .maximumSize(300)
//                        .recordStats()
//                        .build()
//        );
//
//        manager.setCaches(List.of(auctionsDefault));
//        return manager;
//    }
//}
