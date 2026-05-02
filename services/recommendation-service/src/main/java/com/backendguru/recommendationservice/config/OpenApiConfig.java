package com.backendguru.recommendationservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Recommendation Service API",
            version = "v1",
            description =
                "Content-based product recommendations + MCP server for AI agents (Spring AI)"),
    servers = {@Server(url = "/", description = "Default")})
public class OpenApiConfig {}
