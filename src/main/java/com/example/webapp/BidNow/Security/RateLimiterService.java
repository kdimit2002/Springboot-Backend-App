    package com.example.webapp.BidNow.Security;

    import com.example.webapp.BidNow.Services.EmailService;
    import com.github.benmanes.caffeine.cache.Cache;
    import org.springframework.beans.factory.annotation.Qualifier;
    import org.springframework.stereotype.Component;

    import java.util.concurrent.atomic.AtomicInteger;

    /**
     * Rate limiting filter
     *
     * Through caching
     */
    @Component
    public class RateLimiterService {

        // ban duration (αλλάζεις ελεύθερα)
        private static final long BAN_MILLIS = 60L * 60_000L; // 1 hour
        private static final long WINDOW_MILLIS = 60_000L;   // 1 minute window

        private final EmailService emailService;
        private final Cache<String, RateLimitState> stateCache;// todo: change to database id in the future
        private final Cache<String, Long> banCache;

        public RateLimiterService(
                EmailService emailService, @Qualifier("rateLimitStateCache") Cache<String, RateLimitState> stateCache,
                @Qualifier("rateLimitBanCache") Cache<String, Long> banCache
        ) {
            this.emailService = emailService;
            this.stateCache = stateCache;
            this.banCache = banCache;
        }

        public enum DecisionType { ALLOWED, BLOCKED }

        public record Decision(DecisionType type, long retryAfterMinutes, int currentCount) {}

        /**
         * @param key   π.χ. "uid or "ip"
         */
        public Decision check(String key, String method, String uid, String ip) {
            long now = System.currentTimeMillis();
            int limit = 90;

            // 1) If banned stop request
            Long banUntil = banCache.getIfPresent(key);
            if (banUntil != null) {
                if (now < banUntil) {
                    long retryMinutes = Math.max(1, (banUntil - now) / 60_000);
                    return new Decision(DecisionType.BLOCKED, banUntil, 0);
                } else {
                    banCache.invalidate(key);
                }
            }

            // 2) Minute-aligned window id
            long windowId = now / WINDOW_MILLIS;

            RateLimitState state = stateCache.get(key, k -> new RateLimitState(windowId));

            // reset when windowId changes (next minute)
            if (state.windowId != windowId) {
                synchronized (state) { // Lock other threads here and the first thread to come here will update cache(synchronized -> concurrency purpose).
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

            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "Rate limit abuse - temporarily blocked",
                    "Blocked key: " + key +
                            "\nmethod: " + method +
                            "\nuid: " + (uid == null ? "-" : uid) +
                            "\nip: " + ip
            );

            long retryMinutes = Math.max(1, (until - now) / 60_000);
            return new Decision(DecisionType.BLOCKED, retryMinutes, current);
        }

        public static class RateLimitState {
            public volatile long windowId;// All threads will be informed if this changes
            public final AtomicInteger counter = new AtomicInteger(0);

            public RateLimitState(long windowId) {
                this.windowId = windowId;
            }
        }
    }
