package com.backendguru.sellerservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Seller Service API",
            version = "v1",
            description = "Marketplace V1 — sellers + per-seller listings"),
    servers = {@Server(url = "/", description = "Default")})
public class OpenApiConfig {}
