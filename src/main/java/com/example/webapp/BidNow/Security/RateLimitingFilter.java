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

/**
 * Rate limiting filter.
 *
 * Idea:
 * - For authenticated requests we rate-limit per user (Firebase UID).
 * - For anonymous requests we rate-limit per client IP.
 *
 *
 * When a key exceeds the limit, we return HTTP 429 with a Retry-After header.
 * We also send an alert email.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;

    public RateLimitingFilter(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();


        // Find user's firebaseId
        String uid = resolveUid();

        boolean anonymous = (uid == null);

        // Find user's ip
        String ip = resolveClientIp(request);

        // If user is anonymous rate limit by ip else by uid
        //todo: checking ip alone is dangerous. We also have to use an external rate limit provider.(e.g. through load balancer)
        String identity = anonymous ? ("ip:" + ip) : ("uid:" + uid);

        RateLimiterService.Decision decision = rateLimiter.check(identity, request.getMethod(),uid,ip);

        if (decision.type() == RateLimiterService.DecisionType.BLOCKED) {

            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterMinutes()));
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again later.\"," +
                            "\"retryAfterMinutes\":" + decision.retryAfterMinutes() + "}"
            );
            return;
        }

        chain.doFilter(request, response);

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
