package com.example.webapp.BidNow.Security;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 *
 * Security configuration file
 *
 * - Assigns the order of filters:
 *  1) Authentication filter
 *  2) Rate limiting filter
 *  todo: 3)xss sanitization filter
 *
 * - Stateless API (no server sessions) using Bearer JWT (Firebase ID token).
 *
 */
@Configuration
@EnableMethodSecurity // Enable @PreAuthorize,@PostAuthorize etc..
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           FirebaseAuthenticationFilter firebaseFilter,
                                           RateLimitingFilter rateLimitingFilter) throws Exception {
         http
                 // Enable CORS using the CorsConfigurationSource bean below
                 .cors(Customizer.withDefaults())
                 // Stateless REST API: disable CSRF (we are not using cookies-based sessions for auth)
                 .csrf(AbstractHttpConfigurer::disable)
                 // No HTTP Session created/used by Spring Security
                 .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                 // Developer / tooling endpoints (Remove all except last 3 before production)
                 .authorizeHttpRequests(reg -> reg
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/h2-console",
                                "/h2-console/**",
                                // Public auth helper endpoints
                                "/api/auth/username-availability**",
                                "/api/auth/username-availability",
                                "/api/auth/user-availability",
                                "/auth/token"
                        ).permitAll()
                         // WebSocket handshake endpoint (this endpoint is invoked inside rest apis)
                        .requestMatchers("/ws", "/ws/**").permitAll()
                         // Public read-only endpoints (browse auctions without login)
                        .requestMatchers(HttpMethod.GET, "/auctions/**").permitAll()
                         // Public categories endpoint
                        .requestMatchers("/api/categories/**").permitAll()
                         .anyRequest().authenticated())
                 // Disable default auth mechanisms we don't use
                 .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)// maybe remove
                // todo: .addFilterBefore(xssFilter, FirebaseAuthenticationFilter.class)
                 // Firebase auth filter (authenticates and puts uid in SecurityContext)
                 .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
        // Rate limiting filter per user
                .addFilterAfter(rateLimitingFilter, FirebaseAuthenticationFilter.class);
        // Allow H2 console to render in a browser frame (local/dev convenience)
        // todo: remove this before prod
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();


    }


    /**
     * CORS configuration for the frontend .
     * - Allows the Vite dev server origin.
     * - Allows common HTTP methods.
     * - Allows credentials (cookies/authorization headers) if needed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));// Allow requests only that comes only from this url, and from this port
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
