package com.mocktalkback.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .name(BEARER_AUTH)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 또는 /api/auth/refresh 응답의 access token (Bearer 접두사 없이 토큰만 입력)");

        Parameter acceptLanguage = new Parameter()
                .in("header")
                .name("Accept-Language")
                .description("Response locale for error.reason and validation messages. Default: ko. Supported: ko, en.")
                .required(false)
                .schema(new StringSchema()._default("ko").addEnumItem("ko").addEnumItem("en"));

        return new OpenAPI()
                .info(new Info()
                        .title("Mocktalk API")
                        .description("Mocktalk Backend REST API. Optional header Accept-Language: ko (default) or en for localized error.reason and validation messages.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, bearerScheme)
                        .addParameters("AcceptLanguage", acceptLanguage))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}