package com.example.webapp.BidNow.Security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author Kendeas
 */
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        // 1) Αν έχεις ορίσει GOOGLE_APPLICATION_CREDENTIALS, μπορείς να κάνεις:
        // FirebaseOptions options = FirebaseOptions.builder()
        //        .setCredentials(GoogleCredentials.getApplicationDefault())
        //        .setProjectId("my-project-id")
        //        .build();

        // 2) Εναλλακτικά, φόρτωσε από classpath (για dev):
        try (InputStream in = new ClassPathResource("local-f4b46-firebase-adminsdk-fbsvc-e842917a52.json").getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .setProjectId("local-f4b46") // <-- Βάλε το Project ID σου
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }
}
