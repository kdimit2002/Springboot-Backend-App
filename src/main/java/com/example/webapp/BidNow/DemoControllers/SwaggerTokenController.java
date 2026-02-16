package com.example.webapp.BidNow.DemoControllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@RestController
public class SwaggerTokenController {

    private final WebClient webClient;
    private final String apiKey;

    public SwaggerTokenController(WebClient.Builder builder,
                                  @Value("${firebase.webApiKey}") String apiKey) {
        this.webClient = builder.build();
        this.apiKey = apiKey;
    }

    // Firebase response (signInWithPassword)
    record FirebaseSignInResp(String idToken, String refreshToken, String expiresIn) {}

    @PostMapping(value = "/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> token(
            @RequestParam("username") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "grant_type", required = false) String grantType
    ) {
        FirebaseSignInResp resp = webClient.post()
                .uri("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", password, "returnSecureToken", true))
                .retrieve()
                .bodyToMono(FirebaseSignInResp.class)
                .block();

        // OAuth2-style response for Swagger UI
        return Map.of(
                "access_token", resp.idToken(),
                "token_type", "Bearer",
                "expires_in", Integer.parseInt(resp.expiresIn()),
                "refresh_token", resp.refreshToken()
        );
    }
}
