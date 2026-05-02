package com.backendguru.notificationservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Notification Service API",
            version = "v1",
            description = "Async notification consumer (RabbitMQ)"),
    servers = {@Server(url = "/", description = "Default")})
public class OpenApiConfig {}
