//package com.example.webapp.BidNow.Configs;
//
//import io.swagger.v3.oas.models.OpenAPI;
//import io.swagger.v3.oas.models.info.Info;
//import io.swagger.v3.oas.models.security.SecurityRequirement;
//import io.swagger.v3.oas.models.security.SecurityScheme;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class SwaggerConfig {
//
//    @Bean
//    public OpenAPI customOpenAPI() {
//        return new OpenAPI()
//                .info(new Info()
//                        .title("BidNow API")
//                        .version("1.0.0")
//                        .description("API documentation for BidNow"))
//                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
//                .components(new io.swagger.v3.oas.models.Components()
//                        .addSecuritySchemes("Bearer Authentication",
//                                new SecurityScheme()
//                                        .name("Authorization")
//                                        .type(SecurityScheme.Type.HTTP)
//                                        .scheme("bearer")
//                                        .bearerFormat("JWT")
//                                        .description("Enter Firebase ID Token (Bearer <token>)")));
//    }
//}

package com.example.webapp.BidNow.Configs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.OAuthFlow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {

        // password flow -> Swagger
        SecurityScheme firebasePassword = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows()
                        .password(new OAuthFlow()
                                .tokenUrl("/auth/token")
                        )
                )
                .description("Login with email/password to obtain Firebase ID token");

        //  Manual Bearer
        SecurityScheme bearerAuth = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste Firebase ID Token (Bearer <token>)");

        return new OpenAPI()
                .info(new Info()
                        .title("BidNow API")
                        .version("1.0.0")
                        .description("API documentation for BidNow"))
                .components(new Components()
                        .addSecuritySchemes("firebasePassword", firebasePassword)
                        .addSecuritySchemes("bearerAuth", bearerAuth)
                )
                .addSecurityItem(new SecurityRequirement().addList("firebasePassword"));
    }
}
