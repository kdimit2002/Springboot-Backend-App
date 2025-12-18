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
 * @Author Kendeas
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
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }



        String idToken = authHeader.substring(7);
        try {
            // true => απορρίπτει και tokens που έχουν ανακληθεί
            FirebaseToken decodedToken = (isSensitiveEndpoint(request)) ?
                    firebaseAuth.verifyIdToken(idToken, true) // Safe verify but slow retryable
                    : firebaseAuth.verifyIdToken(idToken, false); // fast verify



            String uid = decodedToken.getUid();
            Map<String, Object> claims = decodedToken.getClaims();


            List<String> roles = List.of();

            //ToDo: check if user exists in my database user entity Repository check!
//
//            if(!userEntityRepository.existsByFirebaseId(uid)){
//                //ToDo: logging
//                SecurityContextHolder.clearContext();
//                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                return;
//            }

//            @SuppressWarnings("unchecked")
        //    List<String> roles1 = (List<String>) claims.getOrDefault("roles", Collections.emptyList());

//            if (!isSignUpEndpoint(request) && (roles == null || roles.isEmpty())) {
//                // Χρήστης χωρίς claims → Unauthorized
//                SecurityContextHolder.clearContext();
//                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//                return;
//            }

            // Todo: Prosoxi isos argo mpori ite na baloume cache meta ite diagrafi
            if(!isSignUpEndpoint(request)) {
                if(!userEntityRepository.existsByFirebaseId(uid)){// elexi an o user iparxi stin basi
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                boolean disabled = userEntityRepository.isUserBanned(uid);//Todo:maybe remove pr cached or indexed if slow
                if (disabled) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                // Todo: indexing database
                UserEntity user = userEntityRepository.findByFirebaseId(uid).orElseThrow
                        (()-> new ResourceNotFoundException("User doesn't exist."));

                roles = user.getRoles().stream().map(Role::getName).toList();

                if (roles.isEmpty()) {
                    // χρήστης χωρίς ρόλο → Forbidden
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

//                if (roles == null || roles.isEmpty()) {// todo: secure?????
//                    // Χρήστης χωρίς claims → Unauthorized
//                    SecurityContextHolder.clearContext();
//                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//                    return;
//                }
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
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        }
        catch (FirebaseAuthException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        catch (DataAccessException e) {
            // DB πρόβλημα: διαχώρισέ το από το auth
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        catch (Exception e) {
            // Απρόσμενο bug → 500 για να μην “θάβεται” ως auth failure
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }
    private boolean isSignUpEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();

        // κρίσιμα endpoints — revocation check = true
        return path.startsWith("/api/auth/signup");
                //|| path.startsWith("/billing");
    }

    private boolean authorizedHttpRequest(HttpServletRequest request) {
        String path = request.getRequestURI();

        // κρίσιμα endpoints — revocation check = true
        return path.startsWith("/api/auth/username-availability");
    }

    private boolean isSensitiveEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();

        // κρίσιμα endpoints — revocation check = true
        return path.startsWith("/api/auth/signup")
        || path.startsWith("/api/auth/deleteUser")
                || path.startsWith("/api/auth/login")
                ||  path.startsWith("/auctions/createAuction")
                ||  path.startsWith("/api/files");
    }


}
