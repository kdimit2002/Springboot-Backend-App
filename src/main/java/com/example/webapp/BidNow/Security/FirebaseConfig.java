package com.example.webapp.BidNow.Security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * This is a configuration file
 *
 * Exposes {@link FirebaseAuth} as a Spring bean for server-side authentication operations.
 *
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /**
     * This bean is for setting up configuration parameters
     * It holds common configuration and state for Firebase APIs
     */
//    @Bean
//    public FirebaseApp firebaseApp() throws IOException {
//        // 1) Set up GOOGLE_APPLICATION_CREDENTIALS, and then:
//        // FirebaseOptions options = FirebaseOptions.builder()
//        //        .setCredentials(GoogleCredentials.getApplicationDefault())
//        //        .setProjectId("myProjectId")
//        //        .build();
//
//        // For development
//        try (InputStream in = new ClassPathResource("local-f4b46-firebase-adminsdk-fbsvc-e842917a52.json").getInputStream()) {
//            FirebaseOptions options = FirebaseOptions.builder()
//                    .setCredentials(GoogleCredentials.fromStream(in))
//                    .setProjectId("local-f4b46")
//                    .build();
//            return FirebaseApp.initializeApp(options);
//        }
//    }


//    @Bean
//    public FirebaseApp firebaseApp() throws IOException {
//        if (!FirebaseApp.getApps().isEmpty()) return FirebaseApp.getInstance();
//
//        String projectId = System.getenv().getOrDefault("FIREBASE_PROJECT_ID", "local-f4b46");
//        GoogleCredentials creds;
//
//        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
//        log.error(path);
//        if (path != null && !path.isBlank()) {
//            try (InputStream in = new java.io.FileInputStream(path)) {
//                creds = GoogleCredentials.fromStream(in);
//            }
//        } else {
//            try (InputStream in = new ClassPathResource("local-f4b46-firebase-adminsdk-fbsvc-e842917a52.json").getInputStream()) {
//                creds = GoogleCredentials.fromStream(in);
//            }
//        }
//
//        FirebaseOptions options = FirebaseOptions.builder()
//                .setCredentials(creds)
//                .setProjectId(projectId)
//                .build();
//
//        return FirebaseApp.initializeApp(options);
//    }


    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String projectId = System.getenv().getOrDefault("FIREBASE_PROJECT_ID", "local-f4b46");

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();

        return FirebaseApp.initializeApp(options);
    }
    /**
     * Load bean that handles all server-side Firebase Authentication actions.
     * Our App server use it to perform a variety of authentication-related operations to
     * firebase authentication.
     * @param app, configuration instance
     */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }
}



