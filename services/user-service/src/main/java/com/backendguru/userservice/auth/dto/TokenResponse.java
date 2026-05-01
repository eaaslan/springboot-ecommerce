package com.backendguru.userservice.auth.dto;

public record TokenResponse(
    String accessToken, String refreshToken, long accessExpiresIn, long refreshExpiresIn) {}
