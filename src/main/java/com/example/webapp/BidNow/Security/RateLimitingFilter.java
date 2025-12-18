package com.example.webapp.BidNow.Security;

import com.example.webapp.BidNow.Services.EmailService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final EmailService emailService;
    private final RateLimiterService rateLimiter;

    public RateLimitingFilter(EmailService emailService, RateLimiterService rateLimiter) {
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

//        // Εξαιρέσεις (προσαρμόζεις όπως θες)
//        if (isExcluded(path)) {
//            chain.doFilter(request, response);
//            return;
//        }

        String uid = resolveUid();
        boolean anonymous = (uid == null);

        String ip = resolveClientIp(request);

        // Ομαδοποιούμε endpoints για να μην δημιουργούμε άπειρα keys (π.χ. /items/123)
        String bucket = classifyBucket(path, request.getMethod());

        int limit = resolveLimit(bucket, anonymous);

        String identity = anonymous ? ("ip:" + ip) : ("uid:" + uid);
        String key = identity + "|bucket:" + bucket;

        RateLimiterService.Decision decision = rateLimiter.check(key, limit);

        if (decision.type() != RateLimiterService.DecisionType.ALLOWED) {

            if (decision.type() == RateLimiterService.DecisionType.BLOCKED_AND_ALERT) {
                emailService.sendSimpleEmailAsync(
                        "bidnowapp@gmail.com",
                        "Rate limit abuse - temporarily blocked",
                        "Blocked key: " + key +
                                "\npath: " + path +
                                "\nmethod: " + request.getMethod() +
                                "\nuid: " + (uid == null ? "-" : uid) +
                                "\nip: " + ip +
                                "\nretryAfterSeconds: " + decision.retryAfterSeconds()
                );
            }

            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again later.\"," +
                            "\"retryAfterSeconds\":" + decision.retryAfterSeconds() + "}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

//    private boolean isExcluded(String path) {
//        return path.startsWith("/swagger")
//                || path.startsWith("/v3/api-docs")
//                || path.startsWith("/h2-console")
//                || path.startsWith("/actuator/health");
//    }

    /**
     * Bucket classification: βάλε εδώ τα δικά σου patterns.
     * Στόχος: λίγες “ομάδες”, όχι unique path κάθε φορά.
     */
    private String classifyBucket(String path, String method) {

        // AUTH endpoints (anonymous)
        if (path.startsWith("/api/auth/login")) return "AUTH_LOGIN";
        if (path.startsWith("/api/auth/username-availability")) return "AUTH_USERNAME_AVAIL";
        if (path.startsWith("/api/auth/user-availability")) return "AUTH_USER_AVAIL";

        // AUCTIONS (anonymous)
        // /auctions και /auctions/{id}
        // Προσοχή: /auctions/123 -> AUCTIONS_ITEM, όχι unique key per id
        if (path.equals("/auctions")) return "AUCTIONS_LIST";
        if (path.startsWith("/auctions/")) return "AUCTIONS_ITEM";

        // default buckets (ανά method)
        return "GENERAL_" + method;
    }


    /**
     * Εδώ ορίζεις τα limits.
     * Anonymous: πιο αυστηρά σε login/register/contact.
     */
    private int resolveLimit(String bucket, boolean anonymous) {
        if (anonymous) {
            return switch (bucket) {
                case "AUTH_LOGIN" -> 20;              // 10/min ανά IP
                case "AUTH_USERNAME_AVAIL" -> 50;     // 20/min ανά IP (anti-enumeration)
                case "AUTH_USER_AVAIL" -> 50;         // 20/min ανά IP

                case "AUCTIONS_LIST" -> 90;           // 60/min ανά IP (λίστα)
                case "AUCTIONS_ITEM" -> 90;          // 120/min ανά IP (views σε auction)

                default -> 90;                        // γενικό anonymous fallback
            };
        }

        // Authenticated users (συνήθως πιο χαλαρά)
        return switch (bucket) {
            case "AUTH_LOGIN", "AUTH_USERNAME_AVAIL", "AUTH_USER_AVAIL" -> 60;
            default -> 240; // π.χ. 240/min ανά uid
        };
    }


    private String resolveUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        if (!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof String s && !s.isBlank() && !"anonymousUser".equalsIgnoreCase(s)) {
            return s; // Firebase UID
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
