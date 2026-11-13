package com.finsight.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finsightOpenApi() {
        SecurityScheme bearer = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("FinSight API")
                        .version("1.0.0")
                        .description("""
                                Personal finance REST API: JWT auth, accounts, categories, transactions,
                                budgets, goals, recurring payments, CSV import/export, live FX rates and
                                AI-powered insights proxied to the FastAPI analytics service.
                                """)
                        .contact(new Contact().name("FinSight").url("https://github.com/"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth", bearer))
                .tags(List.of(
                        new Tag().name("Auth").description("Registration and login"),
                        new Tag().name("Transactions").description("CRUD, filtering and CSV"),
                        new Tag().name("Insights").description("Forecasting, anomalies and auto-categorization")));
    }
}
