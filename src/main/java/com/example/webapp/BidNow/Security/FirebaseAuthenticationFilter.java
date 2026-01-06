package com.example.webapp.BidNow.Security;


import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserActivity;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the main authentication filter
 *
 * Security filter that checks firebase and database if the
 * jwt token that has been sent from a user is a token from firebase,
 * through checking the signature of the token with the firebase admin sdk
 * and then checking if the user exists in database which is the source of truth
 *
 */
@Component
//@Order(2)
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final UserEntityRepository userEntityRepository;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth, UserEntityRepository userEntityRepository) {
        this.firebaseAuth = firebaseAuth;
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        // If there is no Bearer token, we don't authenticate here; the request continues as anonymous.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }



        String idToken = authHeader.substring(7);
        try {
            FirebaseToken decodedToken = (isSensitiveEndpoint(request)) ?
                    firebaseAuth.verifyIdToken(idToken, true) // Safe verify but slow. Verifies token through an api to firebase.
                    : firebaseAuth.verifyIdToken(idToken, false); // fast verify. Verifies token through local cache.



            String uid = decodedToken.getUid();
            Map<String, Object> claims = decodedToken.getClaims();


            List<String> roles = List.of();

            //ToDo: check if user exists in my database user entity Repository check!

            // Todo: Warning this maybe use many of the app resources examine in the future
            // todo: for other ways to do this or by applying caching.

            if(!isSignUpEndpoint(request)) {
                // Reject tokens that are valid in Firebase but do not map to a user in our system.
                if(!userEntityRepository.existsByFirebaseId(uid)){
                    SecurityContextHolder.clearContext(); // Clean security context holder
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                boolean disabled = userEntityRepository.isUserBanned(uid);//Todo:maybe remove pr cached or indexed if slow
                if (disabled) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                // Todo: have to index database and make a direct query for getting the roles

                // Load roles from DB (authorization source of truth).
                UserEntity user = userEntityRepository.findByFirebaseId(uid).orElseThrow
                        (()-> new ResourceNotFoundException("User doesn't exist."));

                roles = user.getRoles().stream().map(Role::getName).toList();

                if (roles.isEmpty()) {
                    // Cannot have a user without roles throw exception
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }

            List<GrantedAuthority> authorities = roles.stream()
                    .filter(Objects::nonNull)
                    .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r.toUpperCase())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(uid, null, authorities);


            String path = request.getRequestURI();

            authentication.setDetails(decodedToken);
            SecurityContextHolder.getContext().setAuthentication(authentication); // Fill in context holder to have his information throughout his request
            filterChain.doFilter(request, response);
        }
        catch (FirebaseAuthException | IllegalArgumentException e) {
            // firebase issues
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        catch (DataAccessException e) {
            // DB issues
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        catch (Exception e) {
            // Unexpected errors
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }
    private boolean isSignUpEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/auth/signup");
    }

    private boolean authorizedHttpRequest(HttpServletRequest request) {
        String path = request.getRequestURI();

        // This endpoint is excluded from the filter because is used before signup to check whether
        // there is already a user with the username provided
        return path.startsWith("/api/auth/username-availability");
    }

    private boolean isSensitiveEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Critical endpoints: these endpoints are not safe to be used if a user has been disabled or
        // deleted.
        // Todo: beacause of the DB checks in the filter maybe we can remove the most!
        return path.startsWith("/api/auth/signup")
        || path.startsWith("/api/auth/deleteUser")
                || path.startsWith("/api/auth/login")
                ||  path.startsWith("/auctions/createAuction")
                ||  path.startsWith("/api/files");
    }


}
