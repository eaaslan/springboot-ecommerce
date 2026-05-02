package com.backendguru.paymentservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Payment Service API",
            version = "v1",
            description = "Internal: Iyzico-shaped payment mock"),
    servers = {@Server(url = "/", description = "Default")})
public class OpenApiConfig {}
