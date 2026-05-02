package com.backendguru.catalogstreamservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Catalog Stream Service API",
            version = "v1",
            description =
                "Reactive read facade over productdb (WebFlux + R2DBC, includes SSE stream)"),
    servers = {@Server(url = "/", description = "Default")})
public class OpenApiConfig {}
