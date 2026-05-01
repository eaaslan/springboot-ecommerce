package com.backendguru.userservice.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
    String secret, long accessTokenTtlMinutes, long refreshTokenTtlDays, String issuer) {}
