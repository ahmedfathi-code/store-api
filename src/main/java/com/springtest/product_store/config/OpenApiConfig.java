package com.springtest.product_store.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// OpenAPI metadata and the JWT bearer scheme used by Swagger UI's "Authorize" button.
// Protected operations opt in with @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH).
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI storeApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Store API")
                        .version("v1")
                        .description("""
                                Product catalog with JWT authentication and ADMIN/USER roles.

                                1. Register and log in via **auth** to get an access token.
                                2. Click **Authorize** and paste the access token (without "Bearer ").
                                3. Product writes need an ADMIN account; reads are public.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
