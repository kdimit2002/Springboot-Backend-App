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
 * @Author Kendeas
 */
@Configuration
@EnableMethodSecurity // για @PreAuthorize κ.λπ.
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           FirebaseAuthenticationFilter firebaseFilter,
                                           RateLimitingFilter rateLimitingFilter) throws Exception {
         http
                 .cors(Customizer.withDefaults())   // ✅ ενεργοποίηση CORS /// NEW
                 .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                                "/api/auth/username-availability**",   // 👈 FIXED
                                "/api/auth/username-availability",   // 👈 FIXED
                                "/api/auth/user-availability"
                        ).permitAll()
                        //άφησε το websocket handshake ελεύθερο
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // Auth endpoints public
                        // 👇 ΔΩΡΕΑΝ πρόσβαση για ΟΛΑ τα GET στο /auctions...
                        .requestMatchers(HttpMethod.GET, "/auctions/**").permitAll()
                        //Δωνεαν πρόσβαση στις κατηγοριες
                        .requestMatchers("/api/categories/**").permitAll()
//                        .requestMatchers("/Auth/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)// maybe remove
                .formLogin(AbstractHttpConfigurer::disable)// maybe remove
                 // 1) Πρώτα XSS sanitization
//               .addFilterBefore(xssFilter, FirebaseAuthenticationFilter.class)// maybe remove
                 // 2) Μετά Firebase auth (βάζει uid στο SecurityContext)
                 .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
        // 3) Μετά rate limiting per user (ή IP)
                .addFilterAfter(rateLimitingFilter, FirebaseAuthenticationFilter.class);
        // ✅ Εδώ είναι ξεχωριστή εντολή, ΟΧΙ μέσα στην αλυσίδα:
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();


    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
