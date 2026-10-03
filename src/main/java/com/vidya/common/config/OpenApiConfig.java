package com.vidya.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI vidyaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vidya — Multi-Tenant School Management Platform API")
                        .description("Production-grade Multi-Tenant SaaS School Management Backend Engine. Supports JWT Bearer Authentication and Multi-Tenant Isolation.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Vidya Engineering Team")
                                .email("engineering@vidya.io")
                                .url("https://vidya.io"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://vidya.io/terms")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT Bearer token to access secured platform endpoints")));
    }
}
