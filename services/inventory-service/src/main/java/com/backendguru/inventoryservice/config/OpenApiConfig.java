package com.backendguru.inventoryservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Inventory Service API",
            version = "v1",
            description = "Internal: stock reservations and commits"),
    servers = {@Server(url = "/", description = "Default")})
public class OpenApiConfig {}
