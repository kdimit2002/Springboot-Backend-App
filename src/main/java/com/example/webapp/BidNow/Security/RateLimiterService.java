package com.example.webapp.BidNow.Security;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiterService {

    // ban duration (αλλάζεις ελεύθερα)
    private static final long BAN_MILLIS = 10L * 60_000L; // 10 λεπτά
    private static final long WINDOW_MILLIS = 60_000L;   // 1 λεπτό window

    private final Cache<String, RateLimitState> stateCache;
    private final Cache<String, Long> banCache;
    private final Cache<String, Boolean> alertCooldownCache;

    public RateLimiterService(
            @Qualifier("rateLimitStateCache") Cache<String, RateLimitState> stateCache,
            @Qualifier("rateLimitBanCache") Cache<String, Long> banCache,
            @Qualifier("rateLimitAlertCooldownCache") Cache<String, Boolean> alertCooldownCache
    ) {
        this.stateCache = stateCache;
        this.banCache = banCache;
        this.alertCooldownCache = alertCooldownCache;
    }

    public enum DecisionType { ALLOWED, BLOCKED, BLOCKED_AND_ALERT }

    public record Decision(DecisionType type, long retryAfterSeconds, int currentCount) {}

    /**
     * @param key   π.χ. "uid:abc|bucket:AUTH_LOGIN" ή "ip:1.2.3.4|bucket:CONTACT"
     * @param limit max requests / window (1 λεπτό)
     */
    public Decision check(String key, int limit) {
        long now = System.currentTimeMillis();

        // 1) Αν είναι banned, κόβουμε
        Long banUntil = banCache.getIfPresent(key);
        if (banUntil != null) {
            if (now < banUntil) {
                long retry = Math.max(1, (banUntil - now) / 1000);
                return new Decision(DecisionType.BLOCKED, retry, 0);
            } else {
                banCache.invalidate(key);
            }
        }

        // 2) Minute-aligned window id
        long windowId = now / WINDOW_MILLIS;

        RateLimitState state = stateCache.get(key, k -> new RateLimitState(windowId));

        // reset όταν αλλάζει λεπτό (σχεδόν σπάνιο σε σχέση με τα requests)
        if (state.windowId != windowId) {
            synchronized (state) {
                if (state.windowId != windowId) {
                    state.windowId = windowId;
                    state.counter.set(0);
                }
            }
        }

        int current = state.counter.incrementAndGet();

        if (current <= limit) {
            return new Decision(DecisionType.ALLOWED, 0, current);
        }

        // 3) Ban
        long until = now + BAN_MILLIS;
        banCache.put(key, until);

        // 4) Alert throttling
        boolean shouldAlert = (alertCooldownCache.getIfPresent(key) == null);
        if (shouldAlert) {
            alertCooldownCache.put(key, Boolean.TRUE);
        }

        long retry = Math.max(1, (until - now) / 1000);
        return new Decision(shouldAlert ? DecisionType.BLOCKED_AND_ALERT : DecisionType.BLOCKED, retry, current);
    }

    public static class RateLimitState {
        public volatile long windowId;
        public final AtomicInteger counter = new AtomicInteger(0);

        public RateLimitState(long windowId) {
            this.windowId = windowId;
        }
    }
}
